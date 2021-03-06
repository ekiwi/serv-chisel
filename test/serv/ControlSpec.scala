// Copyright 2020 The Regents of the University of California
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>

package serv

import org.scalatest.flatspec.AnyFlatSpec
import chisel3.tester._
import chiseltest.internal.WriteVcdAnnotation
import chisel3._

class ControlSpec extends AnyFlatSpec with ChiselScalatestTester  {
  val WithVcd = Seq(WriteVcdAnnotation)
  val NoVcd = Seq()

  it should "elaborate" in {
    test(new Control) { dut => }
  }

}
