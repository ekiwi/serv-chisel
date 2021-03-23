// Copyright 2020 The Regents of the University of California
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>

package formal

import org.scalatest.flatspec.AnyFlatSpec
import paso._
import chisel3._
import chisel3.util._

class RiscVSpec extends UntimedModule {
  val pc = UInt(32.W)
  val reg = Mem(32, UInt(32.W)) // reg(0) remains unused

  private def incPC(): Unit = { pc := pc + 1.U}

  private def readReg(addr: UInt): UInt = {
    require(addr.getWidth == 5)
    Mux(addr === 0.U, 0.U, reg.read(addr))
  }

  private def updateReg(addr: UInt, data: UInt): Unit = {
    require(addr.getWidth == 5)
    require(data.getWidth == 32)
    when(addr =/= 0.U) { reg.write(addr, data) }
  }

  private def rType(in: RTypeIO, op: (UInt, UInt) => UInt): Unit = {
    updateReg(in.rd, op(readReg(in.rs1), readReg(in.rs2)))
    incPC()
  }

  private def iType(in: ITypeIO, op: (UInt, UInt) => UInt): Unit = {
    updateReg(in.rd, op(readReg(in.rs1), in.decodeImm))
    incPC()
  }

  //val add = fun("add").in(new RTypeIO)(rType(_, (a,b) => a + b))
  //val sub = fun("sub").in(new RTypeIO)(rType(_, (a,b) => a - b))
  //val addi = fun("addi").in(new ITypeIO)(iType(_, (a,b) => a + b))

  /*
  val loadWord = fun("loadWord").in(new LoadIO).out(UInt(32.W)) { (in, loadAddr) =>
    // calculate load address and return it
    val addr = readReg(in.rs1) + in.decodeImm
    val alignment = addr(1,0)
    assert(alignment === 0.U, "Can only load aligned addresses")
    // align load address
    loadAddr := addr
    // load value into destination register
    updateReg(in.rd, in.value)
  }

  val loadHalf = fun("loadHalf").in(new LoadIO).out(UInt(32.W)) { (in, loadAddr) =>
    // calculate load address and return it
    val addr = readReg(in.rs1) + in.decodeImm
    val alignment = addr(1,0)
    assert(alignment =/= 3.U, "Can only load aligned addresses")
    // align load address
    loadAddr := addr.head(30) ## 0.U(2.W)
    // align value from memory bus
    val alignedValue = MuxLookup(alignment, in.value(15,0), Seq(1.U -> in.value(23, 8), 2.U -> in.value(31,16)))
    // sign extend
    val value = alignedValue.asSInt().pad(32).asUInt()
    // load value into destination register
    updateReg(in.rd, value)
  }
  
  */

  val loadByte = fun("loadByte").in(new LoadIO).out(UInt(32.W)) { (in, loadAddr) =>
    // calculate load address and return it
    val addr = readReg(in.rs1) + in.decodeImm
    val alignment = addr(1,0)
    // align load address
    loadAddr := addr.head(30) ## 0.U(2.W)
    // align value from memory bus
    val alignedValue = MuxLookup(alignment, in.value(7,0), Seq(1.U -> in.value(15, 8), 2.U -> in.value(23,16), 3.U -> in.value(31,24)))
    // sign extend
    val value = alignedValue.asSInt().pad(32).asUInt()
    // load value into destination register
    updateReg(in.rd, value)
  }
}

class RTypeIO extends Bundle {
  val rs1 = UInt(5.W)
  val rs2 = UInt(5.W)
  val rd = UInt(5.W)
  def toInstruction(funct7: UInt, funct3: UInt, opcode: UInt): UInt = {
    funct7.pad(7) ## rs2 ## rs1 ## funct3.pad(3) ## rd ## opcode.pad(7)
  }
}

class ITypeIO extends Bundle {
  val imm = SInt(12.W)
  val rs1 = UInt(5.W)
  val rd = UInt(5.W)
  def decodeImm: UInt = imm.pad(32).asUInt()
  def toInstruction(funct3: UInt, opcode: UInt): UInt = {
    imm.asUInt() ## rs1 ## funct3.pad(3) ## rd ## opcode.pad(7)
  }
}

class LoadIO extends ITypeIO {
  val value = UInt(32.W)
}

class CompileRiscVSpec extends AnyFlatSpec {
  behavior of "RiscVSpec"

  // manye Chisel/Paso errors are only caught when elaborating
  it should "correctly elaborate" in {
    val spec = UntimedModule(new RiscVSpec)
  }

}