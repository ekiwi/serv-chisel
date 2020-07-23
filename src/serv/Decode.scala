// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package serv

import chisel3._
import chisel3.util._

class Decode extends Module {
  val io = IO(new DecodeIO)

  io.rf.rdAddress  := RegEnable(io.top.wbRdt(11, 7), io.top.wbEn)
  val rs1Address = RegEnable(io.top.wbRdt(19,15), io.top.wbEn)
  io.rf.rs1Address := rs1Address
  io.rf.rs2Address := RegEnable(io.top.wbRdt(24,20), io.top.wbEn)

  val funct3 = RegEnable(io.top.wbRdt(14,12), io.top.wbEn)
  val imm30  = RegEnable(io.top.wbRdt(   30), io.top.wbEn)
  val opcode = RegEnable(io.top.wbRdt( 6, 2), io.top.wbEn)
  val op20 = RegEnable(io.top.wbRdt(20), io.top.wbEn)
  val op21 = RegEnable(io.top.wbRdt(21), io.top.wbEn)
  val op22 = RegEnable(io.top.wbRdt(22), io.top.wbEn)
  val op26 = RegEnable(io.top.wbRdt(26), io.top.wbEn)

  // immediate decoder
  val signbit = RegEnable(io.top.wbRdt(31), io.top.wbEn)
  val imm19_12_20 = RegEnable(io.top.wbRdt(19,12) ## io.top.wbRdt(20), io.top.wbEn)
  val imm7 = RegEnable(io.top.wbRdt(7), io.top.wbEn)
  val imm30_25 = RegEnable(io.top.wbRdt(30,25), io.top.wbEn)
  val imm24_20 = RegEnable(io.top.wbRdt(24,20), io.top.wbEn)
  val imm11_7  = RegEnable(io.top.wbRdt(11, 7), io.top.wbEn)
  val m3 = opcode(4)
  val m2 = (opcode(4) & !opcode(0)) ## ((opcode(1,0) === 0.U) || opcode(2,1) === 0.U)
  val csrOp = opcode(4) && opcode(2) && funct3.orR()
  when(io.count.enabled) {
    imm19_12_20 := Mux(m3, signbit, imm24_20(0)) ## imm19_12_20(8,1)
    imm7        := signbit
    imm30_25    := Mux(m2(1), imm7, Mux(m2(0), signbit, imm19_12_20(0))) ## imm30_25(5,1)
    imm24_20    := imm30_25(0) ## imm24_20(4,1)
    imm11_7     := imm30_25(0) ## imm11_7(4,1)
    when(csrOp && io.csr.dSel) {
      rs1Address := 0.U(1.W) ## rs1Address(4,1)
    }
  }

  val opOrOpimm   = !opcode(4) &&  opcode(2) && !opcode(0)
  io.state.memOp := !opcode(4) && !opcode(2) && !opcode(0)
  io.mem.memOp   := io.state.memOp
  io.state.shiftOp  := opOrOpimm && (funct3(1,0) === 1.U)
  io.state.sltOp    := opOrOpimm && (funct3(2,1) === 1.U)
  io.state.branchOp := opcode(4) & !opcode(2)

  // Matches system opcodes except CSR accesses (funct3 == 0).
  // No idea anymore why the !op21 condition is needed here.
  io.state.eOp := opcode(4) & opcode(2) & !op21 & !(funct3.orR())
  io.csr.eOp := io.state.eOp

  io.state.eBreak := op20
  io.csr.eBreak := io.state.eBreak

  // jal,branch =     imm
  // jalr       = rs1+imm
  // mem        = rs1+imm
  // shift      = rs1
  io.bufreg.rs1En := !opcode(4) || (!opcode(1) && opcode(0))
  io.bufreg.immEn := !opcode(2)

  // Loop bufreg contents for shift operations
  io.bufreg.loop := opOrOpimm

  // Clear LSB of immediate for BRANCH and JAL ops
  // True for BRANCH and JAL
  // False for JALR/LOAD/STORE/OP/OPIMM?
  io.bufreg.clearLsb := opcode(4) && ((opcode(1,0) === 0.U) || (opcode(1,0) === 3.U))

  // Take branch for jump or branch instructions (opcode == 1x0xx) if
  // a) It's an unconditional branch (opcode[0] == 1)
  // b) It's a conditional branch (opcode[0] == 0) of type beq,blt,bltu (funct3[0] == 0) and ALU compare is true
  // c) It's a conditional branch (opcode[0] == 0) of type bne,bge,bgeu (funct3[0] == 1) and ALU compare is false
  // Only valid during the last cycle of INIT, when the branch condition has
  // been calculated.
  io.state.takeBranch := opcode(4) && !opcode(2) && (opcode(0) || (io.top.aluCmp ^ funct3(0)))

  io.control.uType     := !opcode(4) && opcode(2) && opcode(0)
  io.control.jalOrJalr :=  opcode(4) &&              opcode(0)

  // True for jal, b* auipc
  // False for jalr, lui
  io.control.pcRel := (opcode(2,0) === 0.U) || (opcode(1,0) === 3.U) || (opcode(4,3) === 0.U)

  io.control.mRet := opcode(4) && opcode(2) && op21 && !funct3.orR()
  io.csr.mRet := io.control.mRet

  // Write to RD
  // True for OP-IMM, AUIPC, OP, LUI, SYSTEM, JALR, JAL, LOAD
  // False for STORE, BRANCH, MISC-MEM
  io.state.rdOp := (opcode(2) ||
                    (!opcode(2) && opcode(4) && opcode(0)) ||
                    (!opcode(2) && !opcode(3) && !opcode(0))) && io.rf.rdAddress.orR()
  io.alu.doSubtract := opcode(3) && imm30

  /*
    300 0_000 mstatus RWSC
    304 0_100 mie SCWi
    305 0_101 mtvec RW
    340 1_000 mscratch
    341 1_001 mepc  RW
    342 1_010 mcause R
    343 1_011 mtval
    344 1_100 mip CWi
    */

  //true  for mtvec,mscratch,mepc and mtval
  //false for mstatus, mie, mcause, mip
  val csrValid = op20 || (op26 && !op22 && !op21)

  //Matches system ops except eceall/ebreak
  io.top.rdCsrEn := csrOp

  io.rf.csrEnable := csrOp && csrValid
  io.csr.mStatusEn := csrOp && !op26 && !op22
  io.csr.mieEn     := csrOp && !op26 &&  op22 && !op20
  io.csr.mcauseEn  := csrOp          &&  op21 && !op20
  io.csr.source := funct3(1,0)
  io.csr.dSel := funct3(2)
  io.csr.imm := rs1Address(0)
  io.rf.csrAddress := MuxCase[UInt](Csr.Mtvec, Seq(
    (op26 && !op20) -> Csr.Mscratch,
    (op26 && !op21) -> Csr.Mepc,
    ((op26        ) -> Csr.Mtval)))

  io.alu.cmpEqual := funct3(2,1) === 0.U
  io.alu.cmpUnsigned := (funct3(0) && funct3(1)) || (funct3(1) && funct3(2))
  io.alu.shiftSigned := imm30
  io.alu.shiftRight := funct3(2)
  io.alu.boolOp := BooleanOperation(funct3(1,0))
  // one hot encoded
  val aluBool = funct3(2) && !(funct3(1,0) === 1.U)
  val aluSlt = funct3(2,1) === 1.U
  val aluShift = funct3(1,0) === 1.U
  val aluAddSub = funct3 === 0.U
  io.alu.rdSelect := Result(aluBool ## aluSlt ## aluShift ## aluAddSub)

  io.mem.cmd := opcode(3)
  io.mem.signed := !funct3(2)
  io.mem.word := funct3(1)
  io.mem.half := funct3(0)

  // True for S (STORE) or B (BRANCH) type instructions
  // False for J type instructions
  val m1 = opcode(3,0) === "b1000".U
  io.top.imm := Mux(io.count.done, signbit, Mux(m1, imm11_7(0), imm24_20(0)))

  //0 (OP_B_SOURCE_IMM) when OPIMM
  //1 (OP_B_SOURCE_RS2) when BRANCH or OP
  io.top.opBSource := opcode(3)

  io.top.rdAluEn := !opcode(0) && opcode(2) && !opcode(4)
}

class DecodeIO extends Bundle {
  val count = new CountIO
  val state = new DecodeToStateIO
  val bufreg = new DecodeToBufRegIO
  val control = new DecodeToControlIO
  val alu = new DecodeToAluIO
  val rf = new DecodeToRegisterFileIO
  val mem = new DecodeToMemoryIO
  val csr = new DecodeToCsrIO
  val top = new DecodeToTopIO
}

class DecodeToStateIO extends Bundle {
  val takeBranch = Output(Bool())
  val eOp = Output(Bool())
  val eBreak = Output(Bool())
  val branchOp = Output(Bool())
  val memOp = Output(Bool())
  val shiftOp = Output(Bool())
  val sltOp = Output(Bool())
  val rdOp = Output(Bool())
}

class DecodeToBufRegIO extends Bundle {
  val loop = Output(Bool())
  val rs1En = Output(Bool())
  val immEn = Output(Bool())
  val clearLsb = Output(Bool())
}

class DecodeToControlIO extends Bundle {
  val jalOrJalr = Output(Bool())
  val uType = Output(Bool())
  val pcRel = Output(Bool())
  val mRet = Output(Bool())
}

class DecodeToAluIO extends Bundle {
  val doSubtract = Output(Bool())
  val boolOp = Output(BooleanOperation())
  val cmpEqual = Output(Bool())
  val cmpUnsigned = Output(Bool())
  val shiftSigned = Output(Bool())
  val shiftRight = Output(Bool())
  val rdSelect = Output(Result())
}

class DecodeToRegisterFileIO extends Bundle {
  val rdAddress = Output(UInt(5.W)) // i_rd_waddr
  val rs1Address = Output(UInt(5.W))
  val rs2Address = Output(UInt(5.W))
  val csrAddress = Output(UInt(2.W)) // i_csr_addr, csr_addr
  val csrEnable = Output(Bool()) // i_csr_en, csr_en,
}

class DecodeToMemoryIO extends Bundle {
  val memOp = Output(Bool())
  val signed = Output(Bool())
  val word = Output(Bool())
  val half = Output(Bool())
  val cmd = Output(Bool())
}

class DecodeToCsrIO extends Bundle {
  val mStatusEn = Output(Bool())
  val mieEn = Output(Bool())
  val mcauseEn = Output(Bool())
  val source = Output(UInt(2.W))
  val dSel = Output(Bool())
  val imm = Output(UInt(1.W))
  val eOp = Output(Bool())
  val eBreak = Output(Bool())
  val mRet = Output(Bool())
}

class DecodeToTopIO extends Bundle {
  val wbRdt = Input(UInt(32.W))
  val wbEn = Input(Bool())
  val aluCmp = Input(Bool())
  val imm = Output(UInt(1.W))
  val opBSource = Output(Bool())
  val rdCsrEn = Output(Bool())
  val rdAluEn = Output(Bool())
}
