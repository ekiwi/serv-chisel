// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package servant

import chisel3._

class WishboneMuxIO extends Bundle {
  val cpu = wishbone.WishBoneIO.Responder(32)
  val mem = wishbone.WishBoneIO.Initiator(32)
  val gpio = wishbone.WishBoneIO.Initiator(0)
  val timer = wishbone.WishBoneIO.Initiator(0)
  val test = wishbone.WishBoneIO.Initiator(32)
}


class WishboneMux extends Module {
  val io = IO(new WishboneMuxIO)

  val select = io.cpu.adr(31,30)
  io.cpu.rdt := Mux(select(1), io.timer.rdt, Mux(select(0), io.gpio.rdt, io.mem.rdt))

  val ack = RegInit(false.B)
  ack := io.cpu.cyc && !ack
  io.cpu.ack := ack

  io.mem.adr := io.cpu.adr
  io.mem.dat := io.cpu.dat
  io.mem.sel := io.cpu.sel
  io.mem.we := io.cpu.we
  io.mem.cyc := io.cpu.cyc & (select === 0.U)

  io.gpio.adr := DontCare
  io.gpio.dat := io.cpu.dat
  io.gpio.sel := "b1111".U
  io.gpio.we := io.cpu.we
  io.gpio.cyc := io.cpu.cyc & (select === 1.U)

  io.timer.adr := DontCare
  io.timer.dat := io.cpu.dat
  io.timer.sel := "b1111".U
  io.timer.we := io.cpu.we
  io.timer.cyc := io.cpu.cyc & (select === 2.U)

  io.test.adr := io.cpu.adr
  io.test.dat := io.cpu.dat
  io.test.sel := "b1111".U
  io.test.we := io.cpu.we
  io.test.cyc := io.cpu.cyc & (select === 2.U) // somehow it looks like timer and test device might be swapped in the original sev...
}
