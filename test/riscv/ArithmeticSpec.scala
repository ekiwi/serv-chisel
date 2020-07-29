// Copyright 2020 The Regents of the University of California
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>

package riscv

import chisel3.tester.experimental.TestOptionBuilder._
import chisel3._
import serv.ServTopWithRam

class ArithmeticSpec extends InstructionSpec  {

  it should "add" in {
    val random = new scala.util.Random(0)
    val DebugMode = false
    test(new ServTopWithRam(true))
      .withAnnotations(if(DebugMode) WithVcd else NoVcd)  { dut =>
        (0 until 20).foreach { _ =>
          val rs1 = random.nextInt(32)
          val rs2 = random.nextInt(32)
          val rd = random.nextInt(31) + 1
          val a = if(rs1 == 0) BigInt(0) else BigInt(32, random)
          val b = if(rs2 == 0) BigInt(0) else BigInt(32, random)
          val WordMask = (BigInt(1) << 32) - 1
          val c = (a + b) & WordMask
          if(DebugMode) println(s"$a (x$rs1) + $b (x$rs2) = $c (x$rd)")

          // load values into registers
          if(rs1 > 0) exec(dut.clock, dut.io, RiscV.loadWord(0, 0, rs1), Load(0.U, a.U))
          if(rs2 > 0) exec(dut.clock, dut.io, RiscV.loadWord(0, 0, rs2), Load(0.U, b.U))
          // do add
          exec(dut.clock, dut.io, RiscV.add(rs1, rs2, rd))
          // store result
          exec(dut.clock, dut.io, RiscV.storeWord(0, 0, rd), Store(0.U, c.U, 15.U))
        }
      }
  }
}
