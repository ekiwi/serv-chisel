// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package servant

import chisel3._
import chisel3.util._

class WishBoneRam(depth: Int = 256) extends Module {
  val addressWidth = log2Ceil(depth)
  val io = IO(wishbone.WishBoneIO.Responder(addressWidth))

  val mem = SyncReadMem(depth / 4, Vec(4, UInt(8.W)))
  val wordAlignedAddress = io.adr(addressWidth - 1, 2)

  val ack = Reg(Bool())
  ack := io.cyc && !ack
  io.ack := ack

  io.rdt := mem.read(wordAlignedAddress).asUInt()

  //val writeData = Seq(io.dat(31,24), io.dat(23,16), io.dat(15,8), io.dat(7,0))
  // asUInt on vectors returns a weird order
  val writeData = Seq(io.dat(7,0), io.dat(15,8), io.dat(23,16), io.dat(31,24))
  val writeMask = io.sel.asBools()
  when(io.cyc && io.we) {
    mem.write(wordAlignedAddress, VecInit(writeData), VecInit(writeMask))
  }
}


