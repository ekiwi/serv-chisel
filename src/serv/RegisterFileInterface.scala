// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package serv

import chisel3._
import chisel3.util._

// This mostly muxes register file, CSR and trap accesses to use the same RAM
class RegisterFileInterface(withCsr: Boolean = true) extends Module {
  val io = IO(new RegisterFileInterfaceIO)

  // Memory Map:
  // mepc  100010
  // mtval 100011
  // csr   1000xx
  // rd    0xxxxx
  val CsrPrefix = "b1000".U
  val MepcAddress = CsrPrefix ## Csr.Mepc
  val MtvalAddress = CsrPrefix ## Csr.Mtval
  val MtvecAddress = CsrPrefix ## Csr.Mtvec


  if(withCsr) {
    ///// write side
    val rd = io.rd.controlData |
      (io.rd.aluData & io.rd.aluEnable) | // result from ALU
      (io.rd.csrData & io.rd.csrEnable) | // result from CSR
      (io.rd.memData) // result from memory

    // port 0: rd, mtval
    val mtval = Mux(io.trap.memMisaligned, io.trap.bufRegData, io.trap.badPC)
    io.rf.write0.data := Mux(io.trap.doTrap, mtval, rd)
    io.rf.write0.addr := Mux(io.trap.doTrap, MtvalAddress, io.decode.rdAddress)
    io.rf.write0.enable := io.trap.doTrap || io.rd.writeEnable

    // port 1: csr, mepc
    io.rf.write1.data := Mux(io.trap.doTrap, io.trap.mePC, io.csr.writeData)
    io.rf.write1.addr := Mux(io.trap.doTrap, MepcAddress, io.decode.csrAddress)
    io.rf.write1.enable := io.trap.doTrap || io.decode.csrEnable

    ///// read side

    // port 0: rs1
    io.rf.read0.addr := io.decode.rs1Address
    io.rs1Data := io.rf.read0.data

    // port 1: rs2 / csr
    io.rf.read1.addr := MuxCase(io.decode.rs2Address, Seq(
      io.trap.doTrap -> MtvecAddress,
      io.trap.mRet -> MepcAddress,
      io.decode.csrEnable -> CsrPrefix ## io.decode.csrAddress
    ))
    io.rs2Data := io.rf.read1.data
    io.csr.readData := io.rf.read1.data & io.decode.csrEnable
    io.trap.csrPC := io.rf.read1.data

  } else { // without CSR
    ///// write side
    val rd = io.rd.controlData |
      (io.rd.aluData & io.rd.aluEnable) | // result from ALU
      (io.rd.memData) // result from memory

    // port 0: rd
    io.rf.write0.data := rd
    io.rf.write0.addr := io.decode.rdAddress
    io.rf.write0.enable := io.rd.writeEnable

    // port 1: unused
    io.rf.write1.data := 0.U
    io.rf.write1.addr := 0.U
    io.rf.write1.enable := false.B

    ///// read side

    // port 0: rs1
    io.rf.read0.addr := io.decode.rs1Address
    io.rs1Data := io.rf.read0.data

    // port 1: rs2
    io.rf.read1.addr := io.decode.rs2Address
    io.rs2Data := io.rf.read1.data

    io.csr.readData := DontCare
    io.trap.csrPC := DontCare
  }
}

class RegisterFileInterfaceIO extends Bundle {
  val rf = Flipped(new RegisterFilePortIO)
  val trap = new RegisterFileTrapIO
  val csr = new RegisterFileToCsrIO
  val rd = new RegisterFileWritePortIO
  val rs1Data = Output(UInt(1.W))
  val rs2Data = Output(UInt(1.W))
  val decode = Flipped(new DecodeToRegisterFileIO)
}

class RegisterFileWritePortIO extends Bundle {
  val writeEnable = Input(Bool()) // i_rd_wen
  val controlData = Input(UInt(1.W)) // i_ctrl_rd
  val aluData = Input(UInt(1.W)) // i_alu_rd
  val aluEnable = Input(Bool()) // i_rd_alu_en
  val csrData = Input(UInt(1.W)) // i_csr_rd
  val csrEnable = Input(Bool()) // i_rd_csr_en
  val memData = Input(Bool()) // i_mem_rd
}

class RegisterFileTrapIO extends Bundle {
  val doTrap = Input(Bool())
  val mRet = Input(Bool())
  val mePC = Input(Bool())
  val memMisaligned = Input(Bool())
  val bufRegData = Input(UInt(1.W))
  val badPC = Input(UInt(1.W))
  val csrPC = Output(UInt(1.W))
}

class RegisterFileReadPortIO extends Bundle {
  val address = Input(UInt(5.W))
  val data = Output(UInt(1.W))
}