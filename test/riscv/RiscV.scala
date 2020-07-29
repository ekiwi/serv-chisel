// Copyright 2020 The Regents of the University of California
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>

package riscv

import chisel3._

object RiscV {
  /** rd = *(rs1 + offset) */
  def loadWord(offset: Int, rs1: Int, rd: Int): UInt = // LW
    i(imm = offset, rs1=rs1, funct3 = "010", rd=rd, opcode = "0000011")
  def loadHalf(offset: Int, rs1: Int, rd: Int): UInt = // LH
    i(imm = offset, rs1=rs1, funct3 = "001", rd=rd, opcode = "0000011")
  def loadByte(offset: Int, rs1: Int, rd: Int): UInt = // LB
    i(imm = offset, rs1=rs1, funct3 = "000", rd=rd, opcode = "0000011")
  def loadHalfUnsigned(offset: Int, rs1: Int, rd: Int): UInt = // LHU
    i(imm = offset, rs1=rs1, funct3 = "101", rd=rd, opcode = "0000011")
  def loadByteUnsigned(offset: Int, rs1: Int, rd: Int): UInt = // LBU
    i(imm = offset, rs1=rs1, funct3 = "100", rd=rd, opcode = "0000011")
  /**  */
  def storeWord(offset: Int, rs1: Int, rs2: Int): UInt = // SW
    s(imm = offset, rs2=rs2, rs1=rs1, funct3 = "010", opcode = "0100011")
  def storeHalf(offset: Int, rs1: Int, rs2: Int): UInt = // SH
    s(imm = offset, rs2=rs2, rs1=rs1, funct3 = "001", opcode = "0100011")
  def storeByte(offset: Int, rs1: Int, rs2: Int): UInt = // SB
    s(imm = offset, rs2=rs2, rs1=rs1, funct3 = "000", opcode = "0100011")

  def add(rs1: Int, rs2: Int, rd: Int): UInt =
    r(funct7 = "0000000", rs2 = rs2, rs1 = rs1, funct3 = "000", rd = rd, opcode = "0110011")

  val MaxImm12: BigInt = (BigInt(1) << 11) - 1
  val MinImm12: BigInt = -(MaxImm12 + 1)

  def i(imm: BigInt, rs1: Int, funct3: String, rd: Int, opcode: String): UInt = {
    assert(imm <= MaxImm12 && imm >= MinImm12)
    assert(rs1 >= 0 && rs1 < 32)
    assert(funct3.length == 3)
    assert(rd >= 0 && rd < 32)
    assert(opcode.length == 7)
    val instr = bitString(imm, 12) + unsigned(rs1, 5) + funct3 + unsigned(rd, 5) + opcode
    assert(instr.length == 32)
    ("b" + instr).U
  }

  def s(imm: BigInt, rs2: Int, rs1: Int, funct3: String, opcode: String): UInt = {
    assert(imm <= MaxImm12 && imm >= MinImm12)
    assert(rs2 >= 0 && rs2 < 32)
    assert(rs1 >= 0 && rs1 < 32)
    assert(funct3.length == 3)
    assert(opcode.length == 7)
    val mask12 = (BigInt(1) << 12) - 1
    val mask5 = (BigInt(1) << 5) - 1
    val immMsb = (imm & mask12) >> 5
    val immLsb = imm & mask5
    val instr = bitString(immMsb, 12 - 5) + unsigned(rs2, 5)  + unsigned(rs1, 5) +
      funct3 + bitString(immLsb, 5) + opcode
    assert(instr.length == 32)
    ("b" + instr).U
  }

  def r(funct7: String, rs2: Int, rs1: Int, funct3: String, rd: Int, opcode: String): UInt = {
    assert(funct7.length == 7)
    assert(rs2 >= 0 && rs2 < 32)
    assert(rs1 >= 0 && rs1 < 32)
    assert(funct3.length == 3)
    assert(rd >= 0 && rd < 32)
    assert(opcode.length == 7)
    val instr = funct7 + unsigned(rs2, 5) + unsigned(rs1, 5) + funct3 + unsigned(rd, 5) + opcode
    assert(instr.length == 32)
    ("b" + instr).U
  }
  private def unsigned(i: Int, width: Int): String = {
    val str = Integer.toBinaryString(i)
    assert(str.length <= width)
    if(str.length == width) { str } else { ("0" * (width - str.length)) + str }
  }
  private def bitString(i: BigInt, width: Int): String = {
    val mask = (BigInt(1) << width) - 1
    val str = (i & mask).toString(2)
    assert(str.length <= width)
    if(str.length == width) { str } else { ("0" * (width - str.length)) + str }
  }
}

class RiscvModel(random: scala.util.Random, debugMode: Boolean = false) {
  private val regs: Array[BigInt] = Seq.tabulate(32)(i => if(i == 0) BigInt(0) else BigInt(32, random)).toArray
  private val regsValid: Array[Boolean] = Seq.tabulate(32)(i => i == 0).toArray
  private val WordMask = (BigInt(1) << 32) - 1
  private def debug(msg: String): Unit = if(debugMode) { println(msg) }
  private def writeResult(rd: Int, result: BigInt, valid: Boolean): Unit = {
    assert(rd >= 0 && rd < 32)
    if(rd > 0) {
      val c = result & WordMask
      debug(f"rd  (x${rd}%02d) = 0x${c}%08x ($valid)")
      regs(rd) = c
      regsValid(rd) = valid
    }
  }
  private def loadOperands(rs1: Int, rs2: Int): (BigInt, BigInt, Boolean) = {
    assert(rs1 >= 0 && rs1 < 32)
    assert(rs2 >= 0 && rs2 < 32)
    val (a,b) = (regs(rs1), regs(rs2))
    assert(a.bitLength <= 32 && a >= 0)
    assert(b.bitLength <= 32 && b >= 0)
    val (validA, validB) = (regsValid(rs1), regsValid(rs2))
    debug(f"rs1 (x${rs1}%02d) = 0x${a}%08x ($validA)")
    debug(f"rs2 (x${rs2}%02d) = 0x${b}%08x ($validB)")
    val valid = validA && validB
    (a, b, valid)
  }
  def add(rd: Int, rs1: Int, rs2: Int): Unit = {
    val (a, b, valid) = loadOperands(rs1, rs2)
    writeResult(rd, a + b, valid)
  }
}