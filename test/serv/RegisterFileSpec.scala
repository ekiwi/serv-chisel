// Copyright 2020 The Regents of the University of California
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>

package serv

import org.scalatest._
import chisel3.tester._
import chisel3._
import chisel3.tester.internal.WriteVcdAnnotation
import chisel3.tester.experimental.TestOptionBuilder._

class RegisterFileSpec extends FlatSpec with ChiselScalatestTester  {
  val WithVcd = Seq(WriteVcdAnnotation)
  val NoVcd = Seq()

  def expectRead2(clock: Clock, io: RegisterFileIO, addr: BigInt, data: BigInt) {
    io.writeRequest.poke(false.B)
    io.readRequest.poke(true.B)
    clock.step()
    io.readRequest.poke(false.B)
    io.read0.addr.poke(addr.U)
    (0 to 31).foreach { ii =>
      val bit = (data >> ii) & 1
      io.read0.data.expect(bit.U)
      clock.step()
    }
  }

  def write2(clock: Clock, io: RegisterFileIO, addr: BigInt, data: BigInt) {
    io.writeRequest.poke(true.B)
    io.readRequest.poke(false.B)
    clock.step()
    io.writeRequest.poke(false.B)
    io.write0.enable.poke(true.B)
    io.write0.addr.poke(addr.U)
    (0 to 31).foreach { ii =>
      val bit = (data >> ii) & 1
      io.write0.data.poke(bit.U)
      clock.step()
    }
    io.write0.enable.poke(false.B)
  }


  it should "read 0 from location 0 (width = 2)" in {
    test(new RegisterFileWrapper(2)).withAnnotations(NoVcd)  { dut =>
      expectRead2(dut.clock, dut.io, 0, 0)
    }
  }

  it should "read value that was written to location 1 (width = 2)" in {
    test(new RegisterFileWrapper(2)).withAnnotations(WithVcd) { dut =>
      val value = BigInt("1234", 16)
      write2(dut.clock, dut.io, 1, value)
      expectRead2(dut.clock, dut.io, 1, value)
    }
  }


}

class RegisterFileWrapper(width: Int, csrRegs: Int = 4) extends Module {
  val io = IO(new RegisterFileIO())
  val interface = Module(new RegisterFileInterface(width, csrRegs))
  val ram = Module(new Ram(width, interface.depth))
  io <> interface.io.rf
  ram.io <> interface.io.ram
}