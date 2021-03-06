// Copyright 2020 The Regents of the University of California
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>

package riscv

import java.io.{BufferedInputStream, ByteArrayOutputStream}
import org.scalatest.flatspec.AnyFlatSpec
import chisel3.tester._
import chiseltest.internal.{VerilatorBackendAnnotation, WriteVcdAnnotation}
import chisel3.tester.experimental.TestOptionBuilder.ChiselScalatestOptionBuilder
import servant.Servant
import scala.collection.mutable


class ComplianceTest extends AnyFlatSpec with ChiselScalatestTester {
  val WithVcd = Seq(WriteVcdAnnotation)
  val NoVcd = Seq()
  val WithVerilator = Seq(VerilatorBackendAnnotation)


  it should "pass the ADD test" in {
    val program = ComplianceTest.load("ADD")
    val memSize = 8192
    test(new Servant(memSize, program)).withAnnotations(WithVerilator ++ WithVcd) { dut =>
      dut.clock.setTimeout(10000000)
      var done = false
      val output = new mutable.ArrayBuffer[BigInt]()
      while(!done) {
        if(dut.test.dataValid.peek().litToBoolean) {
          val data = dut.test.data.peek().litValue() & 0xff
          output.append(data)
        }
        if(dut.test.stop.peek().litToBoolean) {
          println("Test complete")
          done = true
        }
        dut.clock.step()
      }

      val result = output.map(_.toChar).mkString("")
      println(result)
    }
  }
}

object ComplianceTest {
  def load(name: String): Array[BigInt] = {
    require(tests.contains(name))
    val filename = s"/rv32i/I-$name-01.elf.bin"
    val input = new BufferedInputStream(getClass.getResourceAsStream(filename))
    val bytes = readBytes(input)
    input.close()
    bytes
  }

  private def readBytes(input: BufferedInputStream): Array[BigInt] = {
    val data = new Array[Byte](4000)
    val result = new ByteArrayOutputStream()
    var count: Int = input.read(data)
    while(count > 0) {
      result.write(data, 0, count)
      count = input.read(data)
    }
    result.toByteArray.map(b => BigInt(b & 0xff))
  }

  val tests = List(
    "ADD",
    "ADDI",
    "AND",
    "ANDI",
    "AUIPC",
    "BEQ",
    "BGE",
    "BGEU",
    "BLT",
    "BLTU",
    "BNE",
    "DELAY_SLOTS",
    "EBREAK",
    "ECALL",
    "ENDIANESS",
    "IO",
    "JAL",
    "JALR",
    "LB",
    "LBU",
    "LH",
    "LHU",
    "LUI",
    "LW",
    "MISALIGN_JMP",
    "MISALIGN_LDST",
    "NOP",
    "OR",
    "ORI",
    "RF_size",
    "RF_width",
    "RF_x0",
    "SB",
    "SH",
    "SLL",
    "SLLI",
    "SLT",
    "SLTI",
    "SLTIU",
    "SLTU",
    "SRA",
    "SRAI",
    "SRL",
    "SRLI",
    "SUB",
    "SW",
    "XOR",
    "XORI",
  )
}
