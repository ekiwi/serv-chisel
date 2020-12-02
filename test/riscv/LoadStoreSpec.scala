// Copyright 2020 The Regents of the University of California
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>

package riscv

import chisel3.tester.experimental.TestOptionBuilder._
import chisel3._
import serv.ServTopWithRam

class LoadStoreSpec extends InstructionSpec  {
  it should "load a word" in {
    test(new ServTopWithRam(true)).withAnnotations(NoVcd)  { dut =>
      val i = RiscV.loadWord(0xa8, 0, 1) // load address 0xab into x1
      exec(dut.clock, dut.io, i, Load(0xa8.U, "haaaaaaaa".U))
    }
  }


  it should "load and store a word" in {
    test(new ServTopWithRam(true)).withAnnotations(NoVcd)  { dut =>
      val value = "haaaaaaaa".U
      val lw = RiscV.loadWord(0xa8, 0, 1) // load address 0xab into x1
      val sw = RiscV.storeWord(0xa0, 0, 1) // store content of x1 to address 0xa0
      exec(dut.clock, dut.io, lw, Load(0xa8.U, value))
      exec(dut.clock, dut.io, sw, Store(0xa0.U, value, 15.U))
    }
  }

  it should "load and store words (random)" in {
    val random = new scala.util.Random(0)
    test(new ServTopWithRam(true)).withAnnotations(NoVcd)  { dut =>
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

  it should "load and store words, halfs and bytes (random)" ignore {
    val random = new scala.util.Random(0)
    test(new ServTopWithRam(true)).withAnnotations(NoVcd)  { dut =>
      (0 until 40).foreach { _ =>
        val value = BigInt(32, random)
        val reg = random.nextInt(31) + 1
        val loadSize = Size.getRandom(random)
        val loadUnsigned = random.nextBoolean()
        val storeSize = Size.getRandom(random)
        // we can only test positive offsets atm since we use x0 and negative addresses do not make sense
        val baseLoadOffset = BigInt(11, random)
        val baseStoreOffset = BigInt(11, random)

        // println(s"value=$value, reg=$reg, offset=$offset")

        // aligns offset depending on load/store size
        val loadOffset  = baseLoadOffset // TODO: align
        val storeOffset = baseStoreOffset //TODO: align

        // encode instructions
        val lw = loadSize match {
          case Size.Word => RiscV.loadWord(loadOffset.toInt, 0, reg)
          case Size.Half => if(loadUnsigned) {
            RiscV.loadHalfUnsigned(loadOffset.toInt, 0, reg)
          } else {
            RiscV.loadHalf(loadOffset.toInt, 0, reg)
          }
          case Size.Byte => if(loadUnsigned) {
            RiscV.loadByteUnsigned(loadOffset.toInt, 0, reg)
          } else {
            RiscV.loadByte(loadOffset.toInt, 0, reg)
          }
        }
        val sw = storeSize match {
          case Size.Word => RiscV.storeWord(storeOffset.toInt, 0, reg)
          case Size.Half => RiscV.storeHalf(storeOffset.toInt, 0, reg)
          case Size.Byte => RiscV.storeByte(storeOffset.toInt, 0, reg)
        }

        val expectedSel = storeSize match {
          case Size.Word => "b1111".U
          case Size.Half => "b0011".U
          case Size.Byte => "b0001".U
        }

        val loadMask = Size.toMask(loadSize)
        val loadValue = if(loadUnsigned || loadSize == Size.Word) {
          value & loadMask
        } else {
          val sign = if(loadSize == Size.Half) (value >> 15) & 1 else (value >> 7) & 1
          val signMask: BigInt = if(sign == 0) 0 else {
            if(loadSize == Size.Half) ((BigInt(1) << 16) - 1) << 16
            else ((BigInt(1) << 24) - 1) << 8
          }
          (value & loadMask) | signMask
        }

        exec(dut.clock, dut.io, lw, Load(loadOffset.U, value.U))
        // the upper bits are actually undefined ...
        exec(dut.clock, dut.io, sw, Store(storeOffset.U, loadValue.U, expectedSel))
      }
    }
  }
}

object Size extends Enumeration {
  val Word, Half, Byte = Value
  def getRandom(random: scala.util.Random): this.Value = random.nextInt(3) match {
    case 0 => Word
    case 1 => Half
    case 2 => Byte
  }
  def toMask(v: this.Value): BigInt = v match {
    case Size.Word => (BigInt(1) << 32) - 1
    case Size.Half => (BigInt(1) << 16) - 1
    case Size.Byte => (BigInt(1) <<  8) - 1
  }
  def generateAlignedLSBs(random: scala.util.Random, v: this.Value): BigInt = v match {
    case Size.Word => BigInt(0)
    case Size.Half => random.nextInt(3)
    case Size.Byte => random.nextInt(4)
  }
  def generateAlignedAddress(random: scala.util.Random, v: this.Value): BigInt = {
    val lsbs = generateAlignedLSBs(random, v)
    val msbs = BigInt(30, random)
    (msbs << 2) | lsbs
  }
  private val mask4 = (BigInt(1) << 4) - 1
  def getWriteMask(v: this.Value, addr: BigInt): BigInt = v match {
    case Size.Word => mask4
    case Size.Half => (BigInt(3) << (addr & 3).toInt) & mask4
    case Size.Byte => (BigInt(1) << (addr & 3).toInt) & mask4
  }
}
