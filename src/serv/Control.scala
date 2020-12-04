// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package serv

import chisel3._

class Control(resetPc: Int = 0, withCsr: Boolean = true) extends Module{
  val io = IO(new ControlIO)

  val trap = io.state.trap || io.decode.mRet

  val enablePc = RegInit(true.B)
  val pc = RegInit(resetPc.U(32.W))

  val offsetA = io.decode.pcRel & pc
  val offsetB = Mux(io.decode.uType, io.data.imm & io.count.count12To31, io.data.buf)
  val pcPlusOffsetCarry = Reg(UInt(1.W))
  val pcPlusOffset = offsetA +& offsetB + pcPlusOffsetCarry
  pcPlusOffsetCarry := io.state.pcEnable & pcPlusOffset(1)
  val pcPlusOffsetAligned = pcPlusOffset(0) & enablePc
  io.data.badPc := pcPlusOffsetAligned

  val plus4 = io.count.count2
  val pcPlus4Carry = Reg(UInt(1.W))
  val pcPlus4 = pc(0) +& plus4 + pcPlus4Carry
  pcPlus4Carry := io.state.pcEnable & pcPlus4(1)

  val newPc = Wire(Bool())
  if(withCsr) {
    when(io.state.trap) {
      newPc := io.data.csrPc & enablePc
    } .elsewhen(io.state.jump) {
      newPc := pcPlusOffsetAligned
    } .otherwise {
      newPc := pcPlus4(0)
    }
  } else {
    when(io.state.jump) {
      newPc := pcPlusOffsetAligned
    } .otherwise {
      newPc := pcPlus4(0)
    }
  }

  io.data.rd := (io.decode.uType & pcPlusOffsetAligned) | (pcPlus4(0) & io.decode.jalOrJalr)

  io.ibus.cyc := enablePc && !io.state.pcEnable
  io.ibus.address := pc

  when(io.state.pcEnable) {
    enablePc := true.B
    pc := newPc ## pc(31,1)
  } .elsewhen(io.ibus.cyc && io.ibus.ack) {
    enablePc := false.B
  }
}


class ControlIO extends Bundle {
  val state = Flipped(new StateToControlIO)
  val count = new CountIO
  val decode = Flipped(new DecodeToControlIO)
  val ibus = new ControlToInstructionBus
  val data = new ControlDataIO
}

class ControlToInstructionBus extends Bundle {
  val address = Output(UInt(32.W))
  val cyc = Output(Bool())
  val ack = Input(Bool())
}

class ControlDataIO extends Bundle {
  val imm = Input(UInt(1.W))
  val buf = Input(UInt(1.W))
  val csrPc = Input(UInt(1.W))
  val rd = Output(UInt(1.W))
  val badPc =  Output(UInt(1.W))
}

