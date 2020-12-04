// Copyright 2020 The Regents of the University of California
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>

package servant

import org.scalatest._
import chisel3.tester._


class ServantSpec extends FlatSpec with ChiselScalatestTester  {

  it should "elaborate" in {
    val program = Seq.fill(8192)(BigInt(0))
    val memSize = program.size
    test(new Servant(memSize, program)) { dut =>

    }
  }

}
