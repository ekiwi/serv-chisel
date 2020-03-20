// Copyright 2020 The Regents of the University of California
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>

package utils

import org.scalatest._
import chisel3.tester._
import chisel3._
import chisel3.tester.internal.VerilatorBackendAnnotation
import chisel3.tester.experimental.TestOptionBuilder._

class SplitTestModule(width: Int, splitAt: Int) extends Module {
  val io = IO(new Bundle() {
    val in = Input(UInt(width.W))
    val msb = Output(UInt(width.W))
    val lsb = Output(UInt(width.W))
    val concat_msb_lsb = Output(UInt(width.W))
  })
  io.msb := io.in.split(splitAt).msb
  io.lsb := io.in.split(splitAt).lsb
  io.concat_msb_lsb := io.msb ## io.lsb
}

class SplitSpec extends FlatSpec with ChiselScalatestTester {
  val annos = Seq(VerilatorBackendAnnotation)

  it should "split bits correctly" in {
    test(new SplitTestModule(width = 4, splitAt = 3)).withAnnotations(annos) { dut =>
      dut.io.in.poke("b1000".U)
      dut.io.msb.expect("b1".U)
      dut.io.lsb.expect("b000".U)
      dut.io.concat_msb_lsb.expect("b1000".U)
    }
  }
}
