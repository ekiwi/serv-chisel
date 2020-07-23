// Copyright 2020 The Regents of the University of California
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>

package serv

import org.scalatest._
import chisel3.tester._
import chiseltest.internal.WriteVcdAnnotation
import chisel3._

class StateSpec extends FlatSpec with ChiselScalatestTester  {
  val WithVcd = Seq(WriteVcdAnnotation)
  val NoVcd = Seq()

  it should "elaborate" in {
    test(new State) { dut => }
  }

  it should "count correctly" in {
    test(new State) { dut =>
      // all starts with a 1 clk cycle pulse from the register file
      dut.io.ram.ready.poke(true.B)
      // all the outputs should be zero at this point
      dut.io.count.enabled.expect(false.B)
      dut.io.count.done.expect(false.B)

      dut.clock.step()
      dut.io.ram.ready.poke(false.B)

      (0 until 32).foreach { ii =>
        dut.io.count.enabled.expect(true.B)
        dut.io.count.count0.expect(if(ii == 0) { true.B } else { false.B })
        dut.io.count.count1.expect(if(ii == 1) { true.B } else { false.B })
        dut.io.count.count2.expect(if(ii == 2) { true.B } else { false.B })
        dut.io.count.count3.expect(if(ii == 3) { true.B } else { false.B })
        dut.io.count.count7.expect(if(ii == 7) { true.B } else { false.B })
        dut.io.count.count0To3.expect(if(ii >= 0 && ii <= 3) { true.B } else { false.B })
        dut.io.count.count12To31.expect(if(ii >= 12 && ii <= 31) { true.B } else { false.B })
        dut.io.count.done.expect(if(ii == 31) { true.B } else { false.B })
        dut.clock.step()
      }
    }
  }
}
