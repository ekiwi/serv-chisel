// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package serv

import chisel3._

// see https://github.com/SymbioticEDA/riscv-formal/blob/master/docs/rvfi.md
class RiscvFormalInterface extends Bundle {
  val valid = Bool()
  val order = UInt(64.W)
  val insn = UInt(32.W)
  val trap = Bool()
  val halt = Bool()
  val intr = Bool()
  val mode = UInt(2.W)
  val ixl = UInt(2.W)
  val rs1_addr = UInt(4.W)
  val rs2_addr = UInt(4.W)
  val rs1_rdata = UInt(32.W)
  val rs2_rdata = UInt(32.W)
  val rd_addr = UInt(4.W)
  val rd_wdata = UInt(32.W)
  val pc_rdata = UInt(32.W)
  val pc_wdata = UInt(32.W)
  val mem_addr = UInt(32.W)
  val mem_rmask = UInt(4.W)
  val mem_wmask = UInt(4.W)
  val mem_rdata = UInt(32.W)
  val mem_wdata = UInt(32.W)
}

class InstructionMonitorIO extends Bundle {
  val countDone = Input(Bool())
  val pcEnable = Input(Bool())
  val rfReady = Input(Bool())
  val init = Input(Bool())
  val decode = Input(new DecodeToStateIO)
  val rf = Input(new RegisterFileInterfaceIO)
  val ibus = Input(wishbone.WishBoneIO.ReadOnlyInitiator(32))
  val dbus = Input(wishbone.WishBoneIO.Initiator(32))
}

class InstructionMonitor extends MultiIOModule {
  val io = IO(new InstructionMonitorIO)

  val rsEnable = Mux(io.decode.branchOp || io.decode.memOp || io.decode.shiftOp || io.decode.sltOp, io.init, io.pcEnable)

  val valid = RegNext(io.countDone && io.pcEnable, false.B)

  val pc = RegInit(0.U(32.W))
  val nextPc = io.ibus.adr
  when(valid) { pc := nextPc }

  val order = RegInit(0.U(64.W))
  order := order + valid
  val instruction = RegInit(0.U(32.W))
  when(io.ibus.cyc && io.ibus.ack) { instruction := io.ibus.rdt }
  val trap = RegNext(Mux(valid, true.B, io.rf.trap.doTrap), false.B)

  val rdAddress = RegInit(0.U(5.W))
  val rs1Address = RegInit(0.U(5.W))
  val rs2Address = RegInit(0.U(5.W))

  when(io.rfReady) {
    rdAddress := io.rf.decode.rdAddress
    rs1Address := io.rf.decode.rs1Address
    rs2Address := io.rf.decode.rs2Address
  }

  val rdData = RegInit(0.U(32.W))
  when(io.rf.rf.write0.enable) {
    rdData := io.rf.rf.write0.data ## rdData(31,1)
  }

  val rs1Data = RegInit(0.U(32.W))
  val rs2Data = RegInit(0.U(32.W))
  when(rsEnable) {
    rs1Data := io.rf.rs1Data ## rs1Data(31,1)
    rs2Data := io.rf.rs2Data ## rs2Data(31,1)
  }

  when(io.countDone && io.pcEnable && !io.rf.rd.writeEnable) {
    rdAddress := 0.U
    when(!io.rf.decode.rdAddress.orR()) { rdData := 0.U }
  }

  val memAddress = RegInit(0.U(32.W))
  val memReadMask = RegInit(0.U(4.W))
  val memWriteMask = RegInit(0.U(4.W))
  val memReadData = RegInit(0.U(32.W))
  val memWriteData = RegInit(0.U(32.W))
  val memReadActive = io.dbus.cyc && io.dbus.ack && !io.dbus.we
  val memWriteActive = io.dbus.cyc && io.dbus.ack && io.dbus.we

  when(io.dbus.ack) {
    memAddress := io.dbus.adr
    memReadMask := Mux(io.dbus.we, 0.U, io.dbus.sel)
    memWriteMask := Mux(io.dbus.we, io.dbus.sel, 0.U)
    memReadData := io.dbus.rdt
    memWriteData := io.dbus.dat
  }

  when(io.ibus.ack) {
    memReadMask := 0.U
    memWriteMask := 0.U
  }
}

class InstructionPrinter extends InstructionMonitor {
  when(valid) {
    printf("pc = %x ; r[%d] = %x ; r[%d] = %x ; r[%d] = %x ;\n",
      pc, rs1Address, rs1Data, rs2Address, rs2Data, rdAddress, rdData)
  }
  when(memReadActive && memReadMask =/= 0.U) {
    printf("mem[%x] & %x -> %x\n", memAddress, memReadMask, memReadData)
  }
  when(memWriteActive && memWriteMask =/= 0.U) {
    printf("mem[%x] & %x <- %x\n", memAddress, memWriteMask, memWriteData)
  }
}

/*
class RiscvFormalMonitor extends Module {

  // Constants
  val halt = false.B
  val interrupt = false.B
  val mode = 2.U(2.W)
  val ixl = 1.U(2.W)

}
 */
