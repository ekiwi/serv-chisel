// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package serv

import chisel3._
import chisel3.util._

// aka serv_rf_top
class ServTopWithRam(withCsr: Boolean, rfWidth: Int = 2) extends Module {
  val io: ServTopWithRamIO = IO(new ServTopWithRamIO)
  val csrRegs = if(withCsr) 4 else 0

  val top = Module(new ServTop(withCsr))
  val ramInterface = Module(new RamInterface(rfWidth, csrRegs))
  val ramDepth = 32 * (32 + csrRegs) / rfWidth
  val ram = Module(new Ram(rfWidth, ramDepth))

  top.io.timerInterrupt := io.timerInterrupt
  top.io.ibus <> io.ibus
  top.io.dbus <> io.dbus

  top.io.rf <> ramInterface.io.rf
  ram.io <> ramInterface.io.ram
}

class ServTopWithRamIO extends Bundle {
  val timerInterrupt = Input(Bool())
  val ibus = wishbone.WishBoneIO.ReadOnlyInitiator(32)
  val dbus = wishbone.WishBoneIO.Initiator(32)
}
