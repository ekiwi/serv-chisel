package wishbone

import chisel3._


class ReadOnlyWishBoneIO(val addressWidth: Int) extends Bundle {
  val adr = Output(UInt(addressWidth.W))
  val cyc = Output(Bool())
  val rdt = Input(UInt(32.W))
  val ack = Input(Bool())
}

class WishBoneIO(addressWidth: Int) extends ReadOnlyWishBoneIO(addressWidth) {
  val dat = Output(UInt(32.W))
  val sel = Output(UInt(4.W))
  val we = Output(Bool())
}

object WishBoneIO {
  def ReadOnlyInitiator(addressWidth: Int): ReadOnlyWishBoneIO = new ReadOnlyWishBoneIO(addressWidth)
  def Initiator(addressWidth: Int): WishBoneIO = new WishBoneIO(addressWidth)
  def Responder(addressWidth: Int): WishBoneIO = Flipped(new WishBoneIO(addressWidth))
}