// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package serv

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util._

object BooleanOperation extends ChiselEnum {
  val Xor = Value(0.U(2.W))
  val Eq  = Value(1.U(2.W))
  val Or  = Value(2.U(2.W))
  val And = Value(3.U(2.W))
}

object Result extends ChiselEnum {
  val Add   = Value("b0001".U)
  val Shift = Value("b0010".U)
  val Lt    = Value("b0100".U)
  val Bool  = Value("b1000".U)
}

/* Connects ALU to Decoder */
class AluControlIO extends Bundle {
  val opBIsRS2 = Input(Bool()) // instead of immediate
  val doSubtract = Input(Bool())
  val boolOp = Input(BooleanOperation())
  val cmpEqual = Input(Bool())
  val cmpUnsigned = Input(Bool())
  val cmpResult = Output(UInt(1.W))
  val shiftAmountEnable = Input(Bool())
  val shiftSigned = Input(Bool())
  val shiftRight = Input(Bool())
  val rdSelect = Input(Result())
}

class AluDataIO extends Bundle {
  val rs1 = Input(UInt(1.W))
  val rs2 = Input(UInt(1.W))
  val imm = Input(UInt(1.W))
  val buffer = Input(UInt(1.W))
  val rd = Output(UInt(1.W))
}

/* global counter */
class CountIO extends Bundle {
  val init = Input(Bool())
  val value = Input(UInt(5.W))
  val enabled = Input(Bool())
  val count0 = Input(Bool())
  val done = Input(Bool())

}

class AluIO extends Bundle {
  val ctrl = new AluControlIO()
  val data = new AluDataIO()
  val count = new CountIO()
  val shiftDone = Output(Bool())
}

class Alu extends Module {
  val io = IO(new AluIO)

  val operandB = Mux(io.ctrl.opBIsRS2, io.data.rs2, io.data.imm)

  // ~b + 1 (negate B operand)
  val plus1 = io.count.count0
  val negativeBCarry = Reg(UInt(1.W))
  val negativeBCarryAndResult = ~operandB +& plus1 + negativeBCarry
  negativeBCarry := negativeBCarryAndResult(1)
  val negativeB = negativeBCarryAndResult(0)


  // adder
  val addB = Mux(io.ctrl.doSubtract, negativeB, operandB)
  val addCarry = Reg(UInt(1.W))
  val addCarryNextAndResult = io.data.rs1 +& addB + addCarry
  addCarry := io.count.enabled & addCarryNextAndResult(1)
  val resultAdd = addCarryNextAndResult(0)

  // shifter
  val shiftAmountRegister = Module(new ShiftRegister(0.U(5.W)))
  shiftAmountRegister.io.en := io.ctrl.shiftAmountEnable
  shiftAmountRegister.io.d := Mux(io.ctrl.shiftRight, operandB, negativeB)
  val shiftAmount = shiftAmountRegister.io.par ## shiftAmountRegister.io.q

  val shift = Module(new SerialShift)
  shift.io.load := io.count.init
  shift.io.shiftAmount := shiftAmount
  val shiftAmountMSB = Reg(UInt(1.W))
  when(io.ctrl.shiftAmountEnable) { shiftAmountMSB := negativeB }
  shift.io.shamt_msb := shiftAmountMSB
  shift.io.signbit := io.ctrl.shiftSigned & io.data.rs1
  shift.io.right := io.ctrl.shiftRight
  shift.io.d := io.data.buffer
  io.shiftDone := shift.io.done
  val resultShift = shift.io.q

  // equaity
  val equal = io.data.rs1 === operandB
  val equalBuf = Reg(UInt(1.W))
  val resultEqual = equal & equalBuf
  equalBuf := resultEqual | ~(io.count.enabled)

  // less then
  val ltBuf = Reg(UInt(1.W))
  val ltSign = io.count.done & !io.ctrl.cmpUnsigned
  val resultLt = Mux(equal, ltBuf, operandB ^ ltSign)
  val resultLtBuf = Reg(UInt(1.W))
  when(io.count.enabled) { resultLtBuf := resultLt}

  io.ctrl.cmpResult := Mux(io.ctrl.cmpEqual, resultEqual, resultLt)

  // boolean operations
  val BoolLookupTable = "h8e96".U
  val resultBool = BoolLookupTable(io.ctrl.boolOp.asUInt() ## io.data.rs1 ## operandB)

  // results
  io.data.rd :=
    (io.ctrl.rdSelect.asUInt()(0) & resultAdd)           |
    (io.ctrl.rdSelect.asUInt()(1) & resultShift)         |
    (io.ctrl.rdSelect.asUInt()(2) & resultLtBuf & plus1) |
    (io.ctrl.rdSelect.asUInt()(3) & resultBool)
}



class ShiftRegister[D <: UInt](init: D) extends Module {
  val io = IO(new Bundle {
    val en = Input(Bool())
    val d = Input(UInt(1.W))
    val q = Output(UInt(1.W))
    val par = Output(chiselTypeOf(init))
  })

  val data = RegInit(init)
  io.q := data.tail(1)
  io.par := data.head(init.getWidth - 1)
  when(io.en) {
    data := Cat(io.d, data.head(init.getWidth - 1))
  }
}

class SerialShift extends Module {
  val io = IO(new Bundle {
    val load = Input(Bool())
    val shiftAmount = Input(UInt(5.W))
    val shamt_msb = Input(UInt(1.W))
    val signbit = Input(UInt(1.W))
    val right = Input(Bool())
    val done = Output(Bool())
    val d = Input(UInt(1.W))
    val q = Output(UInt(1.W))
  })

  val cnt = Reg(UInt(6.W))
  val signbit = Reg(UInt(1.W))
  val wrapped = RegNext(cnt.head(1) | io.shamt_msb & !io.right)

  when(io.load) {
    cnt := 0.U
    signbit := io.signbit & io.right
  }.otherwise {
    cnt := cnt + 1.U
  }

  io.done := cnt(4, 0) === io.shiftAmount
  io.q := Mux(io.right =/= wrapped, io.d, signbit)
}