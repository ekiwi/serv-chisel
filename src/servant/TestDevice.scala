// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package servant

import chisel3._

class TestIO extends Bundle {
  val data = Output(UInt(32.W))
  val dataValid = Output(Bool())
  val stop = Output(Bool())
}

class TestDeviceIO extends Bundle {
  val out = Output(new TestIO)
  val bus = wishbone.WishBoneIO.Responder(32)
}

/** Used to send data to the simulation.
 *  In the original serv implementation, this is part of the (wishbone) mux.
 * */
class TestDevice extends Module {
  val io = IO(new TestDeviceIO)

  val ack = RegInit(false.B)
  ack := io.bus.cyc && !ack
  io.bus.ack := ack

  val select = io.bus.adr(31,28)
  val active = io.bus.cyc && io.bus.ack

  io.out.dataValid := (select === 8.U) && active
  io.out.stop := (select === 9.U) && active
  io.out.data := io.bus.dat.tail(8)

  // this is a write only device
  io.bus.rdt := DontCare
}
