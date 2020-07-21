// Copyright 2020 The Regents of the University of California
// Copyright 2019-2020 Olof Kindgren
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>
// based on Olof Kindgren's implementation in Verilog

package serv

import chisel3._
import chisel3.util._
import utils._

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

// serv_rf_ram_if
class RamInterface(width: Int, csrRegs: Int = 4) extends MultiIOModule {
  val depth = 32 * (32 + csrRegs) / width

  val ram = IO(Flipped(new RamIO(width, depth)))
  val rf = IO(new RegisterFileIO())

  val log2Width = log2Ceil(width)

  val readRequestBuffer = RegNext(rf.readRequest, init = false.B)
  val rgnt = RegNext(readRequestBuffer, init= false.B)
  rf.ready := rgnt || rf.writeRequest

  // Write Side
  val writeCount = RegInit(0.U(5.W))
  val writeGo = Reg(Bool())
  val writeData0Buffer = Reg(UInt((width - 1).W))
  val writeData1Buffer = Reg(UInt(width.W))
  val writeEnable0Buffer = RegNext(rf.write0.enable)
  val writeEnable1Buffer = RegNext(rf.write1.enable)
  val writeRequestBuffer = RegNext(rf.writeRequest || rgnt)

  val writeTrigger = if(width == 2) {
    (!writeCount(0), writeCount(0))
  } else {
    throw new RuntimeException(s"width $width != 2 is not supported yet")
  }

  ram.writeData := Mux(writeTrigger._2, writeData1Buffer, rf.write0.data ## writeData0Buffer)
  val writeAddress = Mux(writeTrigger._2, rf.write1.addr, rf.write0.addr)
  ram.writeAddr := writeAddress ## writeCount.split(log2Width).msb
  ram.writeEnable := writeGo && ((writeTrigger._1 && writeEnable0Buffer) || (writeTrigger._2 && writeEnable1Buffer))

  writeData0Buffer := rf.write0.data ## writeData0Buffer.split(width-1).lsb.split(1).msb
  writeData1Buffer := rf.write1.data ## writeData1Buffer.split(width-0).lsb.split(1).msb

  when(writeGo) { writeCount := writeCount + 1.U }
  when(RegNext(rf.writeRequest)) { writeGo := true.B }
  when(writeCount === "b11111".U) { writeGo := false.B }

  // Read Side
  val readCount = Reg(UInt(5.W))
  val readTrigger0 = readCount.split(log2Width).lsb === 1.U
  val readTrigger1 = RegNext(readTrigger0)
  val readAddress = Mux(readTrigger0, rf.read1.addr, rf.read0.addr)

  ram.readAddr := readAddress ## readCount.split(log2Width).msb

  val readData0Buffer = Reg(UInt(width.W))
  readData0Buffer := readData0Buffer.split(1).msb
  when(readTrigger0) { readData0Buffer := ram.readData }
  val readData1Buffer = Reg(UInt((width - 1).W))
  readData1Buffer := readData1Buffer.split(1).msb
  when(readTrigger1) { readData1Buffer := ram.readData.split(1).msb }

  rf.read0.data := readData0Buffer(0)
  rf.read1.data := Mux(readTrigger1, ram.readData(0), readData1Buffer(0))

  readCount := readCount + 1.U
  when(rf.readRequest) { readCount := 0.U }
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
  val memory = SyncReadMem(depth, chiselTypeOf(io.readData))
  when(io.writeEnable) { memory.write(io.writeAddr, io.writeData) }
  io.readData := memory.read(io.readAddr)
  // FIXME: initialize RAM with zeros (treadle does this by default, verilator might not)
  //        We can add a `MemoryScalarInitAnnotation` once we upgrade to Chisel 3.4.
  //        https://github.com/freechipsproject/firrtl/pull/1645

//  when(io.writeEnable) { printf(p"mem[0x${Hexadecimal(io.writeAddr)}] <- 0x${Hexadecimal(io.writeData)}\n") }
//  printf(p"mem[0x${Hexadecimal(io.readAddr)}] -> 0x${Hexadecimal(io.readData)}\n")
}
