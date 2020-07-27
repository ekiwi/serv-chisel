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

  def add(rs1: Int, rs2: Int, rd: Int): UInt = {
    val funct7 = "0000000"
    val funct3 = "000"
    val opcode = "0110011"
    val instruction = funct7 + bitString(rs2, 5) + bitString(rs1, 5) + funct3 + bitString(rd, 5) + opcode
    assert(instruction.length == 32)
    ("b" + instruction).U
  }

  def exec(clock: Clock, dut: ServTopWithRamIO, instruction: UInt): Unit = {
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
    // TODO: deal with instructions that interact with the data bus
    dut.ibus.rdt.poke(0.U) // TODO: random data
    dut.ibus.ack.poke(false.B)

    val MaxCycles = 64
    var cycleCount = 0
    while(!dut.ibus.cyc.peek().litToBoolean) {
      clock.step()
      cycleCount += 1
      assert(cycleCount < MaxCycles)
    }
  }

  it should "add" in {
    val random = new scala.util.Random(0)
    val model = new RiscvModel(random, true)
    test(new ServTopWithRam(true)).withAnnotations(WithVcd)  { dut =>
      val rs1 = random.nextInt(32)
      val rs2 = random.nextInt(32)
      val rd = random.nextInt(32)
      model.add(rd, rs1, rs2)
      exec(dut.clock, dut.io, add(rs1, rs2, rd))
    }
  }


  private def bitString(i: Int, width: Int): String = {
    val str = Integer.toBinaryString(i)
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
