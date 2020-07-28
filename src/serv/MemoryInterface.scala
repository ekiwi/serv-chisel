// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package serv

import chisel3._
import chisel3.util._

/** Interfaces with the Data bus, not to be confused with the Ram which is used for the register file and CSRs. */
class MemoryInterface(withCsr: Boolean) extends Module {
  val io = IO(new MemoryInterfaceIO)


  val byteCountPlusLsb = io.state.byteCount +& io.lsb

  val data = Reg(UInt(32.W))
  val dataEnabled = io.enabled && !byteCountPlusLsb(2)
  when(dataEnabled) { data := io.rs2 ## data(31,1) }
  when(io.dbus.ack) { data := io.dbus.rdt }

  val dataCur =
    ((io.lsb === 3.U) && data(24)) ||
    ((io.lsb === 2.U) && data(16)) ||
    ((io.lsb === 1.U) && data( 8)) ||
    ((io.lsb === 0.U) && data( 0))
  val dataValid = io.decode.word || (io.state.byteCount === 0.U) || (io.decode.half && !io.state.byteCount(1))
  val signBit = RegEnable(dataCur, dataValid)

  io.rd := io.decode.memOp & Mux(dataValid, dataCur, signBit & io.decode.signed)
  val sel_3 = (io.lsb === 3.U) || io.decode.word || (io.decode.half && io.lsb(1))
  val sel_2 = (io.lsb === 2.U) || io.decode.word
  val sel_1 = (io.lsb === 1.U) || io.decode.word || (io.decode.half && !io.lsb(1))
  val sel_0 = (io.lsb === 0.U)
  io.dbus.sel := sel_3 ## sel_2 ## sel_1 ## sel_0

  io.dbus.dat := data

  if(withCsr) {
    val misalign = RegNext((io.lsb(0) && (io.decode.word || io.decode.half)) || (io.lsb(1) && io.decode.word))
    io.state.misaligned := misalign && io.decode.memOp
  } else {
    io.state.misaligned := false.B
  }
}


class MemoryInterfaceIO extends Bundle {
  val enabled = Input(Bool())
  val decode = Input(new DecodeToMemoryIO)
  val state = Flipped(new StateToMemoryIO)
  val lsb = Input(UInt(2.W))
  val rs2 = Input(UInt(1.W))
  val rd = Output(UInt(1.W))
  val dbus = new WishboneDataIO
}

class WishboneDataIO extends Bundle {
  // no cyc, addr, we
  val rdt = Input(UInt(32.W))
  val ack = Input(Bool())
  val dat = Output(UInt(32.W))
  val sel = Output(UInt(4.W))
}