// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package serv

import chisel3._



class State(withCsr: Boolean = true) extends Module {
  val io = IO(new StateIO)

  val init = Reg(Bool())

  // count logic
  val countDone = Reg(Bool())
  val countEnabled = Reg(Bool())
  when(io.rf.ready) { countEnabled := true.B }
  when(countDone) { countEnabled := false.B }

  val count = RegInit(0.U(3.W))
  val countR = RegInit(1.U(4.W))
  count := count + countR(3)
  when(countEnabled) { countR := countR(2,0) ## countR(3) }

  val stageTwoRequest = RegNext(countDone && init)

  // update PC in RUN or TRAP states
  io.control.pcEnable := countEnabled & !init

  io.mem.byteCount := count(2,1)

  io.count.count0To3 := count === 0.U
  io.count.count12To31 := count(2) || (count(1,0) === 3.U)
  io.count.count0 := (count === 0.U) && countR(0)
  io.count.count1 := (count === 0.U) && countR(1)
  io.count.count2 := (count === 0.U) && countR(2)
  io.count.count3 := (count === 0.U) && countR(3)
  val count4 = (count === 1.U) && countR(0)
  io.count.count7 := (count === 1.U) && countR(3)

  io.alu.shiftAmountEnable := (io.count.count0To3 || count4) & init

  //slt*, branch/jump, shift, load/store
  val twoStageOp = io.decode.sltOp || io.decode.memOp || io.decode.branchOp || io.decode.shiftOp

  val stageTwoPending = RegInit(false.B)
  when(countEnabled) { stageTwoPending := init }

  io.dbus.cyc := !countEnabled && stageTwoPending && io.decode.memOp && !io.mem.misaligned

  val trapPending = withCsr.B && ((io.control.jump && io.control.misalign) || io.mem.misaligned)

  // Prepare RF for reads when a new instruction is fetched
  // or when stage one caused an exception (rreq implies a write request too)
  io.rf.readRequest := io.ibus.ack || (stageTwoRequest & trapPending)

  // Prepare RF for writes when everything is ready to enter stage two
  io.rf.writeRequest := (
    (io.decode.shiftOp && io.alu.shiftDone && stageTwoPending) ||
    (io.decode.memOp && io.dbus.ack) ||
    (stageTwoRequest && (io.decode.sltOp | io.decode.branchOp) && !trapPending))

  io.rf.writeEnable := io.decode.rdOp && countEnabled && !init

  // Shift operations require bufreg to hold for one cycle between INIT and RUN before shifting
  io.bufreg.hold := !countEnabled && (stageTwoRequest || !io.decode.shiftOp)


  val controlJump = RegInit(false.B)
  when(countDone) { controlJump := init &&  io.decode.takeBranch }
  io.control.jump := controlJump


}


class StateIO extends Bundle {
  val init = Output(Bool())
  val csr = new StateToCsrIO
  val dbus = new StateToDataBusIO
  val ibus = new StateToInstructionBusIO
  val rf = new StateToRegisterFileIO
  val decode = Flipped(new DecodeToStateIO)
  val count = new StateCountIO
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