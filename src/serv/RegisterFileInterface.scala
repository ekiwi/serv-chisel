// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package serv

import chisel3._

// This mostly muxes register file and CSR accesses to use the same RAM
class RegisterFileInterface(withCsr: Boolean = true) extends Module {
  val io = IO(new RegisterFileInterfaceIO)


  if(withCsr) {
    // write side
    val rd = io.write.controlData |
      (io.write.aluData & io.write.aluEnable) |
      (io.write.csrData & io.write.csrEnable) |
      (io.write.memData)
    



  }

}

class RegisterFileInterfaceIO extends Bundle {
  val rf = Flipped(new RegisterFileIO)
  val trap = new RegisterFileTrapIO
  val csr = new RegisterFileToCsrIO
  val write = new RegisterFileWritePortIO
  val rs1 = new RegisterFileReadPortIO
  val rs2 = new RegisterFileReadPortIO
}

class RegisterFileWritePortIO extends Bundle {
  val writeEnable = Input(Bool()) // i_rd_wen
  val address = Input(UInt(5.W)) // i_rd_waddr
  val controlData = Input(UInt(1.W)) // i_ctrl_rd
  val aluData = Input(UInt(1.W)) // i_alu_rd
  val aluEnable = Input(Bool()) // i_rd_alu_en
  val csrData = Input(UInt(1.W)) // i_csr_rd
  val csrEnable = Input(Bool()) // i_rd_csr_en
  val memData = Input(Bool()) // i_mem_rd
}

class RegisterFileTrapIO extends Bundle {
  val trap = Input(Bool())
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