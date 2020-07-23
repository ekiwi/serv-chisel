// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package serv

import chisel3._
import chisel3.util._

class ServTop(withCsr: Boolean) extends Module {
  val io: TopIO = IO(new TopIO)

  val state = Module(new State)
  val decode = Module(new Decode)
  val bufreg = Module(new BufReg)
  val control = Module(new Control)
  val alu = Module(new Alu)
  val rfInterface = Module(new RegisterFileInterface(withCsr))
  val mem = Module(new MemoryInterface(withCsr))


  ///// External Connections
  // connect data bus
  state.io.dbus.ack := io.dbus.ack
  io.dbus.cyc := state.io.dbus.cyc

  // connect instruction bus
  state.io.ibus.ack := io.ibus.ack

  // connect to Register + CSR RAM
  io.rf.writeRequest := state.io.ram.writeRequest
  io.rf.readRequest := state.io.ram.readRequest
  state.io.ram.ready := io.rf.ready

  ///// Internal Connections
  state.io.decode <> decode.io.state
  state.io.bufreg <> bufreg.io.state
  state.io.control <> control.io.state
  state.io.alu <> alu.io.state
  state.io.mem <> mem.io.state

  decode.io.count := state.io.count


//  val state = new DecodeToStateIO
//  val bufreg = new DecodeToBufRegIO
//  val control = new DecodeToControlIO
//  val alu = new DecodeToAluIO
//  val rf = new DecodeToRegisterFileIO
//  val mem = new DecodeToMemoryIO
//  val csr = new DecodeToCsrIO
//  val top = new DecodeToTopIO






  if(withCsr) {
    val csr = Module(new Csr)

    csr.io.state <> state.io.csr
  } else {
    state.io.csr <> DontCare
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