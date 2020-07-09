// Copyright 2020 The Regents of the University of California
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>

package serv

import org.scalatest._
import chisel3.tester._
import chiseltest.internal.WriteVcdAnnotation

class StateSpec extends FlatSpec with ChiselScalatestTester  {
  val WithVcd = Seq(WriteVcdAnnotation)
  val NoVcd = Seq()

  it should "elaborate" in {
    test(new State) { dut => }
  }
}
