// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package servant

import chisel3._

class ArbiterIO extends Bundle {
  val dbus = Flipped(wishbone.WishBoneIO.Initiator(32))
  val ibus = Flipped(wishbone.WishBoneIO.ReadOnlyInitiator(32))
  val cpu = wishbone.WishBoneIO.Initiator(32)
}

/* Arbitrates between dbus and ibus accesses.
   Relies on the fact that not both masters are active at the same time. */
class Arbiter extends Module {
  val io = IO(new ArbiterIO)

  io.dbus.rdt := io.cpu.rdt
  io.dbus.ack := io.cpu.ack && !io.ibus.cyc

  io.ibus.rdt := io.cpu.rdt
  io.ibus.ack := io.cpu.ack && io.ibus.cyc

  io.cpu.adr := Mux(io.ibus.cyc, io.ibus.adr, io.dbus.adr)
  io.cpu.dat := io.dbus.dat
  io.cpu.sel := io.dbus.sel
  io.cpu.we := io.dbus.we && !io.ibus.cyc
  io.cpu.cyc := io.ibus.cyc || io.dbus.cyc
}
