// Copyright 2020 The Regents of the University of California
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>

package serv

import org.scalatest._
import chisel3.tester._
import chiseltest.internal.WriteVcdAnnotation
import chisel3._

class ServTopSpec extends FlatSpec with ChiselScalatestTester  {
  val WithVcd = Seq(WriteVcdAnnotation)
  val NoVcd = Seq()

  it should "elaborate w/ csr" ignore {
    test(new ServTop(true)) { dut => }
  }

  it should "elaborate w/o csr" ignore {
    test(new ServTop(false)) { dut => }
  }
}
