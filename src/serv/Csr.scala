// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package serv

import chisel3._

object Csr {
  val SourceCsr = 0.U(2.W)
  val SourceExt = 1.U(2.W)
  val SourceSet = 2.U(2.W)
  val SourceClr = 3.U(2.W)

  val Mscratch = 0.U(2.W)
  val Mtvec    = 1.U(2.W)
  val Mepc     = 2.U(2.W)
  val Mtval    = 3.U(2.W)
}
