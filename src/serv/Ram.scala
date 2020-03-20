// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package serv

import chisel3._
import chisel3.util._

class WritePortIO extends Bundle {
  val addr = Input(UInt(6.W))
  val enable = Input(Bool())
  val data = Input(UInt(1.W))
}

class ReadPortIO extends Bundle {
  val addr = Input(UInt(6.W))
  val data = Output(UInt(1.W))
}

class RegisterFileIO extends Bundle {
  val writeRequest = Input(Bool())
  val readRequest = Input(Bool())
  val ready = Output(Bool())
  val write0 = new WritePortIO()
  val write1 = new WritePortIO()
  val read0 = new ReadPortIO()
  val read1 = new ReadPortIO()
}

class RegisterFileInterface(width: Int, csrRegs: Int = 4) extends Module {
  val depth = 32 * (32 + csrRegs) / width
  val io = IO(new Bundle {
    val ram = new RamIO(width, depth)
    val rf = Flipped(new RegisterFileIO())
  })

  val log2Width = log2Ceil(width)

  val rgnt = RegInit(false.B)
  io.rf.ready := rgnt || io.rf.writeRequest

  // Write Side
  val writeCount = RegInit(0.U(5.W))
  val writeGo = Reg(Bool())
  val writeData0Buffer = Reg(UInt((width - 1).W))
  val writeData1Buffer = Reg(UInt(width.W))
  val writeEnable0Buffer = RegNext(io.rf.write0.enable)
  val writeEnable1Buffer = RegNext(io.rf.write1.enable)
  val writeRequestBuffer = RegNext(io.rf.writeRequest || rgnt)

  val writeTrigger = if(width == 2) {
    (!writeCount(0), writeCount(0))
  } else {
    val mask = (1 << log2Width) - 1
    val trigger0 = (writeCount.tail(log2Width) == (mask - 1).U)
    val writeTriggerBuffer =
  }

}

class RamIO(val dataWidth: Int, val depth: Int) extends Bundle {
  val writeAddr = Input(UInt(log2Ceil(depth).W))
  val writeData = Input(UInt(dataWidth.W))
  val writeEnable = Input(Bool())
  val readAddr = Input(UInt(log2Ceil(depth).W))
  val readData = Output(UInt(dataWidth.W))
}

class Ram(width: Int, depth: Int) extends Module {
  val io = IO(new RamIO(dataWidth=width, depth=depth))
  val memory = SyncReadMem(depth, io.readData)
  when(io.writeEnable) { memory.write(io.writeAddr, io.writeData) }
  io.readData := memory.read(io.readAddr)
  // FIXME: initialize RAM with zeros
}
