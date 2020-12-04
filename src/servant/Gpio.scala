// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package servant

import chisel3._

class GpioIO extends Bundle {
  val bus = wishbone.WishBoneIO.Responder(0)
  val output = Output(UInt(1.W))
}

class Gpio extends Module {
  val io = IO(new GpioIO)
  val output = Reg(UInt(1.W))
  io.bus.rdt := output
  when(io.bus.cyc && io.bus.we) {
    output := io.bus.dat(0)
  }
  io.bus.ack := DontCare
  io.output := output
}