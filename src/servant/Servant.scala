// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package servant

import chisel3._
import serv.ServTopWithRam

class ServantIO extends Bundle {
  val q = Output(UInt(1.W))
}

class Servant(memSize: Int, program: Seq[BigInt]) extends Module {
  require(program.size <= memSize)

  val io = IO(new ServantIO)

  val arbiter = Module(new Arbiter)
  val mux = Module(new WishboneMux)
  val gpio = Module(new Gpio)
  val ram = Module(new WishBoneRam(depth = memSize, preload = program))
  val cpu = Module(new ServTopWithRam(withCsr = true))

  mux.io.timer <> DontCare
  mux.io.gpio <> gpio.io.bus
  mux.io.mem <> ram.io
  arbiter.io.cpu <> mux.io.cpu
  arbiter.io.dbus <> cpu.io.dbus
  arbiter.io.ibus <> cpu.io.ibus

  io.q := gpio.io.output

  // TODO: timer
  cpu.io.timerInterrupt := false.B
}
