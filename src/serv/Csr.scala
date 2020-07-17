// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package serv

import chisel3._

class Csr extends Module {
  val io = new CsrIO

  val mStatusMie = Reg(Bool())
  val mStatusMpie = Reg(Bool())
  val mieMtie = Reg(Bool())
  val mStatus = RegNext(io.count.count2 && mStatusMie)
  val mCause3_0 = Reg(UInt(4.W))

  when(io.decode)


}

class CsrIO extends Bundle {
  val count = new CountIO
  val decode = Flipped(new DecodeToCsrIO)
  val memCmd = Input(Bool())
  val memMisaligned = Input(Bool())
  val dataIn = Input(UInt(1.W))   // i_d
  val dataOut = Output(UInt(1.W)) // o_q
}

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
