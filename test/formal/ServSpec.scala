// Copyright 2020 The Regents of the University of California
// released under BSD 2-Clause License
// author: Kevin Laeufer <laeufer@cs.berkeley.edu>

package formal

import org.scalatest.flatspec.AnyFlatSpec
import paso._
import chisel3._
import chisel3.util._
import serv.{ServTopWithRam, ServTopWithRamIO}

class ServProtocols(impl: serv.ServTopWithRam) extends ProtocolSpec[RiscVSpec] {
  val spec = new RiscVSpec

  override val stickyInputs = true


  private def noMemProtocol(clock: Clock, dut: ServTopWithRamIO, instruction: UInt, maxCycles: Int): Unit = {
    // wait for instruction bus to be ready
    // TODO: add back to simplify the proof
    //do_while(!dut.ibus.cyc.peek(), 64) { clock.step() }

    dut.timerInterrupt.poke(false.B) // no interrupts
    dut.dbus.ack.poke(false.B) // no data bus transactions
    // apply instruction
    dut.ibus.rdt.poke(instruction)
    dut.ibus.ack.poke(true.B)
    dut.ibus.cyc.expect(true.B) // transaction should be immediately acknowledged
    clock.step()

    // ibus should be idle while we are executing the instruction
    dut.ibus.rdt.poke(DontCare)
    dut.ibus.ack.poke(false.B)

    // cyc will become true once the instruction has executed
    do_while(!dut.ibus.cyc.peek(), maxCycles) {
      clock.step()
    }

    // one cycle with cyc high
    clock.step()
  }


  protocol(spec.add)(impl.io) { (clock, dut, in) =>
    noMemProtocol(clock, dut, in.toInstruction(funct7 = 0.U, funct3 = 0.U, opcode = "b0110011".U), maxCycles = 34)
  }

  protocol(spec.sub)(impl.io) { (clock, dut, in) =>
    noMemProtocol(clock, dut, in.toInstruction(funct7 = "b0100000".U, funct3 = 0.U, opcode = "b0110011".U), maxCycles = 34)
  }
  protocol(spec.addi)(impl.io) { (clock, dut, in) =>
    noMemProtocol(clock, dut, in.toInstruction(funct3 = 0.U, opcode = "b0010011".U), maxCycles = 34)
  }
}

class ServProof(impl: serv.ServTopWithRam, spec: RiscVSpec) extends ProofCollateral(impl, spec) {
  // map data in the register file to the actual RAM
  mapping { (impl, spec) =>
    forall(0 until 32) { ii =>
      // read and combine 2bit values from ram
      val parts = (0 until 16).reverse.map(jj => impl.ram.memory.read(ii * 16.U + jj.U))
      assert(spec.reg.read(ii) === Cat(parts))
    }
    assert(spec.pc === impl.top.control.pc)
  }

  invariants { impl =>
    // RAM Interface
    assert(impl.ramInterface.writeCount === 0.U)
    assert(!impl.ramInterface.readRequestBuffer)
    assert(!impl.ramInterface.rgnt)
    // the following two assumptions do not seem necessary for BMC to work ...
    assert(!impl.ramInterface.writeGo)
    assert(!impl.ramInterface.writeRequestBuffer)

    // register 0 is always zero
    forall(0 until 16) { ii => assert(impl.ram.memory.read(ii) === 0.U) }

    // State
    assert(impl.top.state.count === 0.U)
    assert(impl.top.state.countR === 1.U)
    assert(!impl.top.state.stageTwoPending)
    assert(!impl.top.state.controlJump)
    assert(!impl.top.state.init)
    assert(!impl.top.state.countEnabled)
    assert(!impl.top.state.stageTwoRequest)
    if(impl.top.hasCsr) {
      assert(!impl.top.state.irqSync)
    }

    // Control
    assert(impl.top.control.enablePc)
  }
}


class ServSpec extends AnyFlatSpec with PasoTester {
  behavior of "serv.ServTopWithRam"

  it should "correctly implement the instructions" in {
    val dbg = DebugOptions(printMCProgress = false, printInductionSys = false, printBaseSys = false)
    val opt = Paso.MCBotr //.copy(strategy = ProofIsolatedMethods)
    test(new ServTopWithRam(true))(new ServProtocols(_)).proof(opt, dbg, new ServProof(_, _))
  }

  it should "bmc?" in {
    val dbg = DebugOptions(traceUntimedElaboration = false)
    test(new ServTopWithRam(true))(new ServProtocols(_)).bmc(Paso.Default, dbg, 100)
  }
}
