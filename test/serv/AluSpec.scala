// Copyright 2020 The Regents of the University of California
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>

package serv

import org.scalatest._
import chisel3.tester._
import chisel3._
import chisel3.tester.experimental.TestOptionBuilder._
import chiseltest.internal.WriteVcdAnnotation

class AluSpec extends FlatSpec with ChiselScalatestTester  {
  val WithVcd = Seq(WriteVcdAnnotation)
  val NoVcd = Seq()

  def calculate(clock: Clock, io: AluIO, conf: AluControlIO => Unit, rs1: BigInt, rs2: BigInt, rd: BigInt) {
    io.count.enabled.poke(false.B)
    clock.step()
    io.count.enabled.poke(true.B)
    io.ctrl.opBIsRS2.poke(true.B)
    conf(io.ctrl)
    (0 until 32).foreach { ii =>
      io.data.rs1.poke(((rs1 >> ii) & 1).U) // TODO: would like to be able to do UInt bit extract
      io.data.rs2.poke(((rs2 >> ii) & 1).U)
      io.data.rd.expect(((rd >> ii) & 1).U)
      clock.step()
    }
  }

  def add(ctrl: AluControlIO) { ctrl.doSubtract.poke(false.B) ; ctrl.rdSelect.poke(Result.Add) }
  def sub(ctrl: AluControlIO) { ctrl.doSubtract.poke(true.B)  ; ctrl.rdSelect.poke(Result.Add) }
  def or(ctrl: AluControlIO)  { ctrl.boolOp.poke(BooleanOperation.Or)  ; ctrl.rdSelect.poke(Result.Bool) }
  def xor(ctrl: AluControlIO) { ctrl.boolOp.poke(BooleanOperation.Xor) ; ctrl.rdSelect.poke(Result.Bool) }
  def and(ctrl: AluControlIO) { ctrl.boolOp.poke(BooleanOperation.And) ; ctrl.rdSelect.poke(Result.Bool) }

  val mask32 = (BigInt(1) << 32) - 1

  it should "correctly execute add" in {
    val random = new scala.util.Random(0)
    test(new Alu).withAnnotations(WithVcd)  { dut =>
      (0 until 40).foreach { _ =>
        val (rs1, rs2) = (BigInt(32, random), BigInt(32, random))
        val rd = (rs1 + rs2) & mask32
        calculate(dut.clock, dut.io, add, rs1, rs2, rd)
      }
    }
  }


}
