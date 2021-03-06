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
  val None  = Value("b0000".U(4.W))
  val Add   = Value("b0001".U(4.W))
  val Shift = Value("b0010".U(4.W))
  val Lt    = Value("b0100".U(4.W))
  val Bool  = Value("b1000".U(4.W))
}

class AluDataIO extends Bundle {
  val rs1 = Input(UInt(1.W))
  val rs2 = Input(UInt(1.W))
  val imm = Input(UInt(1.W))
  val buffer = Input(UInt(1.W))
  val rd = Output(UInt(1.W))
}

class AluIO extends Bundle {
  val decode = Flipped(new DecodeToAluIO)
  val data = new AluDataIO()
  val count = new CountIO()
  val state = Flipped(new StateToAluIO)
}

class Alu extends Module {
  val io = IO(new AluIO)

  val operandB = Mux(io.decode.opBIsRS2, io.data.rs2, io.data.imm)

  // ~b + 1 (negate B operand)
  val plus1 = io.count.count0
  val negativeBCarry = Reg(UInt(1.W))
  val negativeBCarryAndResult = ~operandB +& plus1 + negativeBCarry
  negativeBCarry := io.count.enabled & negativeBCarryAndResult(1)
  val negativeB = negativeBCarryAndResult(0)


  // adder
  val addB = Mux(io.decode.doSubtract, negativeB, operandB)
  val addCarry = Reg(UInt(1.W))
  val addCarryNextAndResult = io.data.rs1 +& addB + addCarry
  addCarry := io.count.enabled & addCarryNextAndResult(1)
  val resultAdd = addCarryNextAndResult(0)

  // shifter
  val shiftAmountSerial = Mux(io.decode.shiftRight, operandB, negativeB)
  val shiftAmount = Reg(UInt(5.W))
  val shift = Module(new SerialShift)
  shift.io.load := io.count.init
  shift.io.shiftAmount := shiftAmount
  val shiftAmountMSB = Reg(UInt(1.W))
  when(io.state.shiftAmountEnable) {
    shiftAmountMSB := negativeB
    shiftAmount := shiftAmountSerial ## shiftAmount(4,1)
  }
  shift.io.shamt_msb := shiftAmountMSB
  shift.io.signbit := io.decode.shiftSigned & io.data.rs1
  shift.io.right := io.decode.shiftRight
  shift.io.d := io.data.buffer
  io.state.shiftDone := shift.io.done
  val resultShift = shift.io.q

  // equality
  val equal = io.data.rs1 === operandB
  val equalBuf = Reg(UInt(1.W))
  val resultEqual = equal & equalBuf
  equalBuf := resultEqual | ~(io.count.enabled)

  // less then
  val ltBuf = Reg(UInt(1.W))
  val ltSign = io.count.done & !io.decode.cmpUnsigned
  val resultLt = Mux(equal, ltBuf, operandB ^ ltSign)
  ltBuf := resultLt & io.count.enabled
  val resultLtBuf = Reg(UInt(1.W))
  when(io.count.enabled) { resultLtBuf := resultLt}

  io.decode.cmpResult := Mux(io.decode.cmpEqual, resultEqual, resultLt)

  // boolean operations
  val BoolLookupTable = "h8e96".U
  val resultBool = BoolLookupTable(io.decode.boolOp.asUInt() ## io.data.rs1 ## operandB)

  // results
  io.data.rd :=
    (io.decode.rdSelect.asUInt()(0) & resultAdd)           |
    (io.decode.rdSelect.asUInt()(1) & resultShift)         |
    (io.decode.rdSelect.asUInt()(2) & resultLtBuf & plus1) |
    (io.decode.rdSelect.asUInt()(3) & resultBool)
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