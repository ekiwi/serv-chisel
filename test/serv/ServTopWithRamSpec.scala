// Copyright 2020 The Regents of the University of California
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>

package serv

import org.scalatest._
import chisel3.tester._
import chiseltest.internal.WriteVcdAnnotation
import chisel3._

class ServTopWithRamSpec extends FlatSpec with ChiselScalatestTester  {
  val WithVcd = Seq(WriteVcdAnnotation)
  val NoVcd = Seq()

  it should "elaborate w/ csr" in {
    test(new ServTopWithRam(true)) { dut => }
  }

  it should "elaborate w/o csr" in {
    test(new ServTopWithRam(false)) { dut => }
  }

  it should "add" in {
    
  }
}

class RiscvModel(random: scala.util.Random) {
  private val regs: Array[BigInt] = Seq.tabulate(32)(i => if(i == 0) BigInt(0) else BigInt(32, random)).toArray
  private val WordMask = (BigInt(1) << 32) - 1
  def add(rd: Int, rs1: Int, rs2: Int): Unit = {
    if(rd  > 0) {
      regs(rd) = (regs(rs1) + regs(rs2)) & WordMask
    }
  }


}
