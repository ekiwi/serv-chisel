// Copyright 2020 The Regents of the University of California
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>

package serv

import org.scalatest.flatspec.AnyFlatSpec
import chisel3.tester._
import chisel3._
import chisel3.tester.experimental.TestOptionBuilder._
import chiseltest.internal.WriteVcdAnnotation

class AluSpec extends AnyFlatSpec with ChiselScalatestTester  {
  val WithVcd = Seq(WriteVcdAnnotation)
  val NoVcd = Seq()

  def calculate(clock: Clock, io: AluIO, conf: DecodeToAluIO => Unit, rs1: BigInt, rs2: BigInt, rd: BigInt) {
    io.count.count0.poke(false.B)
    io.count.enabled.poke(false.B)
    clock.step()
    io.count.count0.poke(true.B)
    io.count.enabled.poke(true.B)
    io.decode.opBIsRS2.poke(true.B)
    conf(io.decode)
    // bit0
    io.data.rs1.poke((rs1 & 1).U)
    io.data.rs2.poke((rs2 & 1).U)
    io.data.rd.expect((rd & 1).U)
    clock.step()
    io.count.count0.poke(false.B)
    // bit1...bit31
    (1 until 32).foreach { ii =>
      io.data.rs1.poke(((rs1 >> ii) & 1).U) // TODO: would like to be able to do UInt bit extract
      io.data.rs2.poke(((rs2 >> ii) & 1).U)
      io.data.rd.expect(((rd >> ii) & 1).U)
      clock.step()
    }
  }

  def add(decode: DecodeToAluIO) { decode.doSubtract.poke(false.B) ; decode.rdSelect.poke(Result.Add) }
  def sub(decode: DecodeToAluIO) { decode.doSubtract.poke(true.B)  ; decode.rdSelect.poke(Result.Add) }
  def or(decode: DecodeToAluIO)  { decode.boolOp.poke(BooleanOperation.Or)  ; decode.rdSelect.poke(Result.Bool) }
  def xor(decode: DecodeToAluIO) { decode.boolOp.poke(BooleanOperation.Xor) ; decode.rdSelect.poke(Result.Bool) }
  def and(decode: DecodeToAluIO) { decode.boolOp.poke(BooleanOperation.And) ; decode.rdSelect.poke(Result.Bool) }

  private val mask32 = (BigInt(1) << 32) - 1
  private def flipBits(value: BigInt) = ~value & mask32

  case class TestConfig(name: String, decode: DecodeToAluIO => Unit, sem: (BigInt, BigInt) => BigInt)

  val tests = Seq(
    TestConfig("add", add, (rs1,rs2) => (rs1 + rs2) & mask32),
    TestConfig("sub", sub, (rs1,rs2) => (rs1 + flipBits(rs2) + 1) & mask32),
    TestConfig("or",  or,  (rs1,rs2) => (rs1 | rs2) & mask32),
    TestConfig("xor", xor, (rs1,rs2) => (rs1 ^ rs2) & mask32),
    TestConfig("and", and, (rs1,rs2) => (rs1 & rs2) & mask32),
  )

  tests.foreach{ conf =>
    it should "correctly execute " + conf.name in {
      val random = new scala.util.Random(0)
      test(new Alu).withAnnotations(WithVcd)  { dut =>
        (0 until 40).foreach { _ =>
          val (rs1, rs2) = (BigInt(32, random), BigInt(32, random))
          val rd = conf.sem(rs1, rs2)
          calculate(dut.clock, dut.io, conf.decode, rs1, rs2, rd)
        }
      }
    }
  }

}
