// Copyright 2020 The Regents of the University of California
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>

package serv

import org.scalatest._
import chisel3.tester._
import chiseltest.internal.WriteVcdAnnotation
import chisel3.tester.experimental.TestOptionBuilder._
import chisel3._

class ServTopWithRamSpec extends FlatSpec with ChiselScalatestTester  {
  val WithVcd = Seq(WriteVcdAnnotation)
  val NoVcd = Seq()

  it should "elaborate w/ csr" in {
    test(new ServTopWithRam(true)) { dut => }
  }

  it should "elaborate w/o csr" in {
    test(new ServTopWithRam(false)) { dut => }
  }

  it should "add" in {
    val random = new scala.util.Random(0)
    val model = new RiscvModel(random, true)
    test(new ServTopWithRam(true)).withAnnotations(WithVcd)  { dut =>
      val rs1 = random.nextInt(32)
      val rs2 = random.nextInt(32)
      val rd = random.nextInt(32)
      val a = BigInt(32, random)
      val b = BigInt(32, random)
      val WordMask = (BigInt(1) << 32) - 1
      val c = (a + b) & WordMask
      println(s"$a + $b = $c")

      // load values into registers
      exec(dut.clock, dut.io, RiscV.loadWord(0, 0, rs1), Load(0.U, a.U))
      exec(dut.clock, dut.io, RiscV.loadWord(0, 0, rs2), Load(0.U, b.U))
      // do add
      exec(dut.clock, dut.io, RiscV.add(rs1, rs2, rd))
      // store result
      exec(dut.clock, dut.io, RiscV.storeWord(0, 0, rd), Store(0.U, c.U, 15.U))
    }
  }

  it should "load a word" in {
    test(new ServTopWithRam(true)).withAnnotations(WithVcd)  { dut =>
      val i = RiscV.loadWord(0xa8, 0, 1) // load address 0xab into x1
      exec(dut.clock, dut.io, i, Load(0xa8.U, "haaaaaaaa".U))
    }
  }


  it should "load and store a word" in {
    test(new ServTopWithRam(true)).withAnnotations(WithVcd)  { dut =>
      val value = "haaaaaaaa".U
      val lw = RiscV.loadWord(0xa8, 0, 1) // load address 0xab into x1
      val sw = RiscV.storeWord(0xa0, 0, 1) // store content of x1 to address 0xa0
      exec(dut.clock, dut.io, lw, Load(0xa8.U, value))
      exec(dut.clock, dut.io, sw, Store(0xa0.U, value, 15.U))
    }
  }

  it should "load and store words (random)" in {
    val random = new scala.util.Random(0)
    test(new ServTopWithRam(true)).withAnnotations(WithVcd)  { dut =>
      (0 until 20).foreach { _ =>
        val value = BigInt(32, random).U
        val reg = random.nextInt(31) + 1
        // we can only test positive offsets atm since we use x0 and negative addresses do not make sense
        // we also need to align the offset
        val offset = (BigInt(9, random) << 2).toInt
        // println(s"value=$value, reg=$reg, offset=$offset")
        val lw = RiscV.loadWord(offset, 0, reg)
        val sw = RiscV.storeWord(offset, 0, reg)
        exec(dut.clock, dut.io, lw, Load(offset.U, value))
        exec(dut.clock, dut.io, sw, Store(offset.U, value, 15.U))
      }
    }
  }


  sealed trait DataBusOp
  case object Nop extends DataBusOp
  case class Store(addr: UInt, value: UInt, sel: UInt) extends DataBusOp
  case class Load(addr: UInt, value: UInt) extends DataBusOp

  def exec(clock: Clock, dut: ServTopWithRamIO, instruction: UInt, dbus: DataBusOp = Nop): Unit = {
    // disable interrupts
    dut.timerInterrupt.poke(false.B)
    // do not ack anything on the data bus
    dut.dbus.ack.poke(false.B)

    // we expect the cpu to be ready to execute a new instruction
    dut.ibus.cyc.expect(true.B)

    // put the instruction on the bus
    dut.ibus.rdt.poke(instruction)
    dut.ibus.ack.poke(true.B)
    clock.step()

    // Now we lower the ack and wait until the cpu is done processing the instruction
    dut.ibus.rdt.poke(0.U) // TODO: random data
    dut.ibus.ack.poke(false.B)

    val MaxCycles = 64 + 64
    var cycleCount = 0

    if(dbus != Nop) {
      // wait for data bus operation
      while(!dut.dbus.cyc.peek().litToBoolean) {
        clock.step() ; cycleCount += 1 ; assert(cycleCount <= MaxCycles)
      }
      clock.step() ; cycleCount += 1 ; assert(cycleCount <= MaxCycles)
      dut.dbus.ack.poke(true.B)
      dbus match {
        case Load(addr, value) =>
          dut.dbus.rdt.poke(value)
          dut.dbus.adr.expect(addr)
          dut.dbus.we.expect(false.B)
        case Store(addr, value, sel) =>
          dut.dbus.adr.expect(addr)
          dut.dbus.sel.expect(sel)
          dut.dbus.dat.expect(value)
          dut.dbus.we.expect(true.B)
      }
      clock.step()
      dut.dbus.ack.poke(false.B)
    }

    while(!dut.ibus.cyc.peek().litToBoolean) {
      clock.step() ; cycleCount += 1 ; assert(cycleCount <= MaxCycles)
    }
  }


}

object RiscV {
  /** rd = *(rs1 + offset) */
  def loadWord(offset: Int, rs1: Int, rd: Int): UInt = // LW
    i(imm = offset, rs1=rs1, funct3 = "010", rd=rd, opcode = "0000011")
  /**  */
  def storeWord(offset: Int, rs1: Int, rs2: Int): UInt = // SW
    s(imm = offset, rs2=rs2, rs1=rs1, funct3 = "010", opcode = "0100011")

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
