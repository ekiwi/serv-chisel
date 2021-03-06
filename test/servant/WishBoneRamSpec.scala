// Copyright 2020 The Regents of the University of California
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>

package servant

import org.scalatest.flatspec.AnyFlatSpec
import chisel3.tester._
import chiseltest.internal.{VerilatorBackendAnnotation, WriteVcdAnnotation}
import chisel3.tester.experimental.TestOptionBuilder._
import chisel3._

class WishBoneRamSpec extends AnyFlatSpec with ChiselScalatestTester  {
  val DebugMode: Boolean = false

  val annos = if(DebugMode) { Seq(WriteVcdAnnotation) } else { Seq() }

  it should "elaborate" in {
    test(new WishBoneRam) { dut => }
  }

  def read(clock: Clock, io: wishbone.WishBoneIO, addr: BigInt, data: BigInt, dataValid: Boolean): Unit = {
    io.adr.poke(addr.U)
    io.cyc.poke(true.B)
    io.we.poke(false.B)
    while(!io.ack.peek().litToBoolean) {
      clock.step()
    }
    if(dataValid) {
      io.rdt.expect(data.U)
    }
    clock.step()
  }

  def write(clock: Clock, io: wishbone.WishBoneIO, addr: BigInt, data: BigInt, mask: BigInt): Unit = {
    assert(mask >= 0 && mask <= 15)
    io.adr.poke(addr.U)
    io.cyc.poke(true.B)
    io.we.poke(true.B)
    io.sel.poke(mask.U)
    io.dat.poke(data.U)
    while(!io.ack.peek().litToBoolean) {
      clock.step()
    }
    clock.step()
  }

  def idle(clock: Clock, io: wishbone.WishBoneIO): Unit = {
    io.cyc.poke(false.B)
    clock.step()
  }

  it should "read preloaded data" in {
    val data = BigInt("11223344", 16)
    val preload = List(BigInt("44", 16), BigInt("33", 16), BigInt("22", 16), BigInt("11", 16))
    test(new WishBoneRam(preload.size, preload)).withAnnotations(annos) { dut =>
      read(dut.clock, dut.io, 0, data, true)
    }
  }

  it should "read and write some data" in {
    val size = 256
    // limiting the address size helps shorten the trace length
    val maxAddress = if(DebugMode) { size } else { size }
    val transactions = if(DebugMode) { 200 } else { 2000 }

    assert(maxAddress <= size)
    val random = new scala.util.Random(0)
    val preload = Seq.tabulate(size)(_ => BigInt(8, random))
    val model = new MemoryModel(size, random, preload)
    test(new WishBoneRam(size, preload)).withAnnotations(annos)  { dut =>
      (0 until transactions).foreach { _ =>
        random.nextInt(3) match {
          case 0 =>
            val addr = BigInt(random.nextInt(maxAddress))
            val (data, valid) = model.read(addr)
            read(dut.clock, dut.io, addr, data, valid)
          case 1 =>
            val addr = BigInt(random.nextInt(maxAddress))
            val data = BigInt(32, random)
            val mask = BigInt(4, random)
            model.write(addr, data, mask)
            write(dut.clock, dut.io, addr, data, mask)
          case 2 =>
            idle(dut.clock, dut.io)
        }
      }
    }
  }
}

class MemoryModel(size: Int, random: scala.util.Random, preload: Iterable[BigInt]= List()) {
  assert(size % 4 == 0)
  assert(preload.isEmpty || preload.size == size)
  private val mem: Array[BigInt] = if(preload.isEmpty) {
    Seq.tabulate(size / 4)(_ => BigInt(32, random)).toArray
  } else {
    val bytes = preload.iterator
    (0 until (size / 4)).map { ii =>
      Seq(bytes.next(), bytes.next() << 8, bytes.next() << 16, bytes.next() << 24).reduce((a,b) => a | b)
    }.toArray
  }
  private val valid: Array[Boolean] = if(preload.isEmpty) {
    Seq.tabulate(size)(_ => false).toArray
  } else {
    Seq.tabulate(size)(_ => true).toArray
  }
  private val AddressMask = ((BigInt(1) << 32) - 1) & (~BigInt(3))
  private val ByteMask = (BigInt(1)<<8) - 1
  private val WordMask = (BigInt(1)<<32) - 1
  def read(addr: BigInt): (BigInt, Boolean) = {
    val alignedAddr = addr & AddressMask
    assert(alignedAddr + 4 <= size, s"Read address $alignedAddr is out off bounds!")
    val data = mem((alignedAddr / 4).toInt)
    val v = (0 until 4).map(i => valid(alignedAddr.toInt + i)).reduce((a,b) => a && b)
    (data, v)
  }
  def write(addr: BigInt, data: BigInt, mask: BigInt): Unit = {
    assert(mask >= 0 && mask <= 15, s"Mask: $mask")
    val alignedAddr = addr & AddressMask
    assert(alignedAddr + 4 <= size, s"Write address $alignedAddr is out off bounds!")
    val writeMask = (0 until 4).map(i => if((mask & (1 << i)) == 0) BigInt(0) else ByteMask << (i*8)).reduce((a,b) => a | b)
    val remainMask = (~writeMask) & WordMask
    val newValue = (data & writeMask) | (mem((alignedAddr / 4).toInt) & remainMask)
    mem((alignedAddr / 4).toInt) = newValue
    // update valid bits
    (0 until 4).foreach { i =>
      if((mask & (1 << i)) != 0) {
        valid(alignedAddr.toInt + i) = true
      }
    }
  }
}
