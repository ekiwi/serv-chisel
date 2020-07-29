// Copyright 2020 The Regents of the University of California
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>

package riscv

import chisel3.tester.experimental.TestOptionBuilder._
import chisel3._
import serv.ServTopWithRam

class LoadStoreSpec extends InstructionSpec  {
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
}
