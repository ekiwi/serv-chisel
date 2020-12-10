// Copyright 2020 The Regents of the University of California
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>

package riscv

import org.scalatest.flatspec.AnyFlatSpec
import chisel3.tester._
import chiseltest.internal.WriteVcdAnnotation
import chisel3._
import serv.{ServTopWithRam, ServTopWithRamIO}

/** base class for all instruction specs  */
abstract class InstructionSpec extends AnyFlatSpec with ChiselScalatestTester {
  val WithVcd = Seq(WriteVcdAnnotation)
  val NoVcd = Seq()


  sealed trait DataBusOp
  case object Nop extends DataBusOp
  case class Store(addr: UInt, value: UInt, sel: UInt) extends DataBusOp
  case class Load(addr: UInt, value: UInt) extends DataBusOp

  def exec(clock: Clock, dut: ServTopWithRamIO, instruction: UInt, dbus: DataBusOp = Nop): Unit = {
    // disable interrupts
    dut.timerInterrupt.poke(false.B)
    // do not ack anything on the data bus
    dut.dbus.ack.poke(false.B)

    // we expect the cpu to be ready to execute a new instruction
    dut.ibus.cyc.expect(true.B)

    // put the instruction on the bus
    dut.ibus.rdt.poke(instruction)
    dut.ibus.ack.poke(true.B)
    clock.step()

    // Now we lower the ack and wait until the cpu is done processing the instruction
    dut.ibus.rdt.poke(0.U) // TODO: random data
    dut.ibus.ack.poke(false.B)

    val MaxCycles = 64 + 64
    var cycleCount = 0

    if(dbus != Nop) {
      // wait for data bus operation
      while(!dut.dbus.cyc.peek().litToBoolean) {
        clock.step() ; cycleCount += 1 ; assert(cycleCount <= MaxCycles)
      }
      clock.step() ; cycleCount += 1 ; assert(cycleCount <= MaxCycles)
      dut.dbus.ack.poke(true.B)
      dbus match {
        case Load(addr, value) =>
          dut.dbus.rdt.poke(value)
          dut.dbus.adr.expect(addr)
          dut.dbus.we.expect(false.B)
        case Store(addr, value, sel) =>
          dut.dbus.adr.expect(addr)
          dut.dbus.sel.expect(sel)
          dut.dbus.dat.expect(value)
          dut.dbus.we.expect(true.B)
      }
      clock.step()
      dut.dbus.ack.poke(false.B)
    }

    while(!dut.ibus.cyc.peek().litToBoolean) {
      clock.step() ; cycleCount += 1 ; assert(cycleCount <= MaxCycles)
    }
  }
}