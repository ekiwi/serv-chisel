// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package serv

import chisel3._
import chisel3.util._

class Top(withCsr: Boolean) extends Module {
  val io: TopIO = IO(new TopIO)

  val state = new State
  val decode = new Decode
  val bufreg = new BufReg
  val control = new Control
  val alu = new Alu
  val rfInterface = ???
  val mem = ???

  if(withCsr) {
    val csr = new Csr
  }
}

class TopIO extends Bundle {
  val timerInterrupt = Input(Bool())
  val rf = Flipped(new RegisterFileIO)
  val ibus = new ReadOnlyWishboneManager
  val dbus = new ReadAndWriteWishboneManager
}

class ReadOnlyWishboneManager extends Bundle {
  val adr = Output(UInt(32.W))
  val cyc = Output(Bool())
  val rdt = Input(UInt(32.W))
  val ack = Input(Bool())
}

class ReadAndWriteWishboneManager extends ReadOnlyWishboneManager {
  val dat = Output(UInt(32.W))
  val sel = Output(UInt(4.W))
  val we = Output(Bool())
}

class TopIOWithFormal extends TopIO {
  val rvfi = new RVFormalInterface
}