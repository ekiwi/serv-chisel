// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package servant

import chisel3._
import chisel3.experimental._
import chisel3.util._
import firrtl.annotations.MemoryArrayInitAnnotation

class WishBoneRam(depth: Int = 256, preload: Seq[BigInt] = List()) extends Module {
  require(depth % 4 == 0)
  require(depth >= 4)
  val addressWidth = log2Ceil(depth)
  val io = IO(wishbone.WishBoneIO.Responder(addressWidth))

  val mem = Seq.fill(4)(SyncReadMem(depth / 4, UInt(8.W)))
  val wordAlignedAddress = if(addressWidth <= 2) 0.U else io.adr(addressWidth - 1, 2)

  val ack = Reg(Bool())
  ack := io.cyc && !ack
  io.ack := ack

  io.rdt := Cat(mem.map(_.read(wordAlignedAddress, true.B)))

  val writeData = Seq(io.dat(31,24), io.dat(23,16), io.dat(15,8), io.dat(7,0))
  val writeMask = io.sel.asBools().reverse
  mem.zip(writeMask).zip(writeData).foreach { case ((m, mask), data) =>
    when(mask && io.cyc && io.we) { m.write(wordAlignedAddress, data) }
  }

  // preload support
  require(preload.isEmpty || preload.size == depth)
  if(preload.nonEmpty) {
    // mem(0) stores the MSB of a word => offset = 3
    // mem(3) stores the LSB of a word => offset = 0
    (0 to 3).zip(mem.reverse).map { case (offset, m) =>
      val groupedBytes = preload.grouped(4)
      val data = groupedBytes.map(_(offset)).toSeq
      annotate(new ChiselAnnotation {
        override def toFirrtl = MemoryArrayInitAnnotation(m.toTarget, data)
      })
    }
  }
}
