// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package serv

import chisel3._
import chisel3.util._

class Csr extends Module {
  val io = IO(new CsrIO)

  val mStatusMie = Reg(Bool())
  val mStatusMpie = Reg(Bool())
  val mieMtie = Reg(Bool())
  val mStatus = RegNext(io.count.count2 && mStatusMie)
  val mCause3_0 = Reg(UInt(4.W))
  val mCause31 = Reg(Bool())

  val mCause = Mux(io.count.count0To3, mCause3_0(0), Mux(io.count.done, mCause31, 0.U))

  val csrOut = ((io.decode.mStatusEn && io.count.enabled && mStatus) ||
    io.rf.readData.asBool() ||
    (io.decode.mcauseEn && io.count.enabled && mCause.asBool()))

  io.dataOut := csrOut

  val csrIn = MuxLookup(io.decode.source, 0.U, Seq(
    Csr.SourceExt -> io.dataIn,
    Csr.SourceSet -> (csrOut | io.dataIn),
    Csr.SourceClr -> (csrOut & !io.dataIn),
    Csr.SourceCsr -> csrOut
  ))

  io.rf.writeData := csrIn

  val timerIrq = io.timerInterrupt && mStatusMie && mieMtie

  io.state.newIrq := !RegNext(timerIrq) && timerIrq

  when(io.decode.mStatusEn && io.count.count3) {
    mStatusMie := io.dataIn
  }

  when(io.decode.mieEn && io.count.count7) {
    mieMtie := io.dataIn
  }

  when(io.decode.mRet) {
    mStatusMie := mStatusMpie
  }

  when(io.state.trapTaken) {
    mStatusMpie := mStatusMie
    mStatusMie := false.B
    mCause31 := io.state.pendingIrq
    when(io.state.pendingIrq) {
      mCause3_0 := 7.U
    } .elsewhen(io.decode.eOp) {
      mCause3_0 := (!io.decode.eBreak) ## "b011".U
    } .elsewhen(io.memMisaligned) {
      mCause3_0 := "b01".U ## io.memCmd ## "b0".U
    } .otherwise {
      mCause3_0 := 0.U
    }
  }

  when(io.decode.mcauseEn && io.count.enabled) {
    when(io.count.count0To3) {
      mCause3_0 := csrIn ## mCause3_0(3,1)
    }
    when(io.count.done) {
      mCause3_0 := csrIn
    }
  }


}

class CsrIO extends Bundle {
  val count = new CountIO
  val decode = Flipped(new DecodeToCsrIO)
  val memCmd = Input(Bool())
  val memMisaligned = Input(Bool())
  val state = Flipped(new StateToCsrIO)
  val rf = Flipped(new RegisterFileToCsrIO)
  val dataIn = Input(UInt(1.W))   // i_d
  val dataOut = Output(UInt(1.W)) // o_q
  val timerInterrupt = Input(Bool())
}

class RegisterFileToCsrIO extends Bundle {
  val readData = Output(UInt(1.W)) // rf_csr_out, o_csr
  val writeData = Input(UInt(1.W)) // i_csr, csr_in, o_csr_in
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
