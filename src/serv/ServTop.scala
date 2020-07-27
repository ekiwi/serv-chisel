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
  io.dbus.adr := bufreg.io.dataBusAddress
  io.dbus.cyc := state.io.dbus.cyc
  io.dbus.dat := mem.io.dbus.dat
  io.dbus.sel := mem.io.dbus.sel
  io.dbus.we := decode.io.mem.cmd
  mem.io.dbus.rdt := io.dbus.rdt
  state.io.dbus.ack := io.dbus.ack
  mem.io.dbus.ack := io.dbus.ack


  // connect instruction bus
  io.ibus.adr := control.io.ibus.address
  io.ibus.cyc := control.io.ibus.cyc
  state.io.ibus.ack := io.ibus.ack
  control.io.ibus.ack := io.ibus.ack
  decode.io.top.wbEn := io.ibus.cyc && io.ibus.ack
  decode.io.top.wbRdt := io.ibus.rdt

  // connect to Register + CSR RAM
  io.rf.writeRequest := state.io.ram.writeRequest
  io.rf.readRequest := state.io.ram.readRequest
  state.io.ram.ready := io.rf.ready

  ///// Internal Connections
  val count = state.io.count
  val imm = decode.io.top.imm
  val lsb = bufreg.io.lsb
  val bufRegData = bufreg.io.dataOut
  val rs1 = rfInterface.io.rs1Data
  val rs2 = rfInterface.io.rs2Data

  state.io.decode <> decode.io.state
  state.io.bufreg <> bufreg.io.state
  state.io.control <> control.io.state
  state.io.alu <> alu.io.state
  state.io.mem <> mem.io.state
  state.io.lsb1 := lsb(1)

  decode.io.count := count
  decode.io.bufreg <> bufreg.io.decode
  decode.io.control <> control.io.decode
  decode.io.alu <> alu.io.decode
  decode.io.rf <> rfInterface.io.decode
  decode.io.mem <> mem.io.decode

  bufreg.io.count := count
  bufreg.io.imm := imm
  bufreg.io.rs1 := rs1

  control.io.count := count
  control.io.data.imm := imm
  control.io.data.buf := bufRegData
  // connect rfInterface and control
  control.io.data.csrPc := rfInterface.io.trap.csrPC
  rfInterface.io.trap.badPC := control.io.data.badPc


  alu.io.count := count
  alu.io.data.rs1 := rs1
  alu.io.data.rs2 := rs2
  alu.io.data.imm := imm
  alu.io.data.buffer := bufRegData

  rfInterface.io.rf <> io.rf.ports
  // write port
  rfInterface.io.rd.writeEnable := state.io.rf.writeEnable
  rfInterface.io.rd.controlData := control.io.data.rd
  rfInterface.io.rd.aluData := alu.io.data.rd
  rfInterface.io.rd.aluEnable := decode.io.top.rdAluEn
  rfInterface.io.rd.csrEnable := decode.io.top.rdCsrEn
  rfInterface.io.rd.memData := mem.io.rd
  // trap interface
  rfInterface.io.trap.doTrap := state.io.control.trap
  rfInterface.io.trap.mRet := decode.io.control.mRet
  rfInterface.io.trap.mePC := io.ibus.adr(0) // ???
  rfInterface.io.trap.memMisaligned := mem.io.state.misaligned
  rfInterface.io.trap.bufRegData := bufRegData

  mem.io.enabled := count.enabled
  mem.io.lsb := lsb
  mem.io.rs2 := rs2

  if(withCsr) {
    val csr = Module(new Csr)
    csr.io.count := count
    csr.io.timerInterrupt := io.timerInterrupt
    csr.io.dataIn := Mux(decode.io.csr.dSel, decode.io.csr.imm, rs1)
    csr.io.memCmd := io.dbus.we
    csr.io.memMisaligned := mem.io.state.misaligned
    state.io.csr <> csr.io.state
    decode.io.csr <> csr.io.decode
    rfInterface.io.rd.csrData := csr.io.dataOut
    rfInterface.io.csr <> csr.io.rf
  } else {
    state.io.csr <> DontCare
    decode.io.csr <> DontCare
    rfInterface.io.csr <> DontCare
    state.io.csr.newIrq := false.B
    rfInterface.io.rd.csrData := 0.U
  }
}

class TopIO extends Bundle {
  val timerInterrupt = Input(Bool())
  val rf = Flipped(new RegisterFileIO)
  val ibus = wishbone.WishBoneIO.ReadOnlyInitiator(32)
  val dbus = wishbone.WishBoneIO.Initiator(32)
}

class TopIOWithFormal extends TopIO {
  val rvfi = new RVFormalInterface
}