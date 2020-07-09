// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package serv

import chisel3._



class State extends Module {
  val io = IO(new StateCountIO)
}


class StateIO extends Bundle {
  val init = Input(Bool())
  val csr = new StateToCsrIO
  val dbus = new StateToDataBusIO
  val ibus = new StateToInstructionBusIO
  val rf = new StateToRegisterFileIO
  val decode = Flipped(new DecodeToStateIO)
  val count = new CountIO
  val bufreg = new StateToBufRegIO
  val control = new StateToControlIO
  val alu = new StateToAluIO
  val mem = new StateToMemIO
}

class StateToCsrIO extends Bundle {
  val newIrq = Input(Bool())
  val trapTaken = Output(Bool())
  val pendingIrq = Output(Bool())
}

class StateToDataBusIO extends Bundle {
  val ack = Input(Bool())
  val cyc = Output(Bool())
}

class StateToInstructionBusIO extends Bundle {
  val ack = Input(Bool())
}

class StateToRegisterFileIO extends Bundle {
  val writeRequest = Output(Bool())
  val readRequest = Output(Bool())
  val ready = Input(Bool())
  val writeEnable = Output(Bool()) // rf_rd_en
}
class StateCountIO extends Bundle {
  val enable = Output(Bool())
  val count0 = Output(Bool())
  val count0To3 = Output(Bool())
  val count12To31 = Output(Bool())
  val count1 = Output(Bool())
  val count2 = Output(Bool())
  val count3 = Output(Bool())
  val count7 = Output(Bool())
  val done = Output(Bool())
}

class StateToBufRegIO extends Bundle {
  val hold = Output(Bool())
}

class StateToControlIO extends Bundle {
  val pcEnable = Output(Bool())
  val jump = Output(Bool())
  val trap = Output(Bool())
  val misalign = Input(Bool())
}

class StateToAluIO extends Bundle {
  val shiftAmountEnable = Output(Bool())
  val shiftDone = Input(Bool())
}

class StateToMemIO extends Bundle {
  val byteCount = Output(UInt(2.W))
  val misaligned = Input(Bool())
}