package ru.r2cloud.sdrmodem;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import com.google.protobuf.ByteString;

import ru.r2cloud.jradio.Context;
import ru.r2cloud.jradio.Endianness;
import ru.r2cloud.jradio.blocks.HdlcReceiver;
import ru.r2cloud.jradio.blocks.HdlcTransmitter;
import ru.r2cloud.jradio.blocks.SoftToHard;
import ru.r2cloud.jradio.blocks.UnpackedToPacked;
import ru.r2cloud.jradio.source.InputStreamSource;

public class LatencyIT {

	private static final String HOSTNAME = "127.0.0.1";

	private static Map<Integer, Long> START_BY_ID = new HashMap<>();
	private static int id = 0;

	public static void main(String[] args) throws Exception {

		runRx();

		Thread.sleep(1000);

		runTx();

		Thread.sleep(100000);
	}

	private static void runRx() throws UnknownHostException, IOException {
		Socket s = new Socket(HOSTNAME, 8091);
		OutputStream os = s.getOutputStream();
		DataOutputStream dos = new DataOutputStream(os);

		Api.doppler_settings.Builder doppler = Api.doppler_settings.newBuilder();
		doppler.setAltitude(0);
		doppler.setLatitude((int) (53.72 * 10E6));
		doppler.setLongitude((int) (47.57F * 10E6));
		doppler.addTle("LUCKY-7");
		doppler.addTle("1 44406U 19038W   20069.88080907  .00000505  00000-0  32890-4 0  9992");
		doppler.addTle("2 44406  97.5270  32.5584 0026284 107.4758 252.9348 15.12089395 37524");

		Api.RxRequest.Builder req = Api.RxRequest.newBuilder();
		req.setRxCenterFreq(633_955_000);
//		req.setRxSamplingFreq(48_000);
		req.setRxSamplingFreq(528000);
		req.setRxOffset(0_000);
		req.setRxDumpFile(true);
		req.setDemodType(Api.modem_type.GMSK);
		req.setDemodBaudRate(9600);
		req.setDemodDecimation(11);
//		req.setDemodDecimation(1);
		req.setDemodDestination(Api.demod_destination.BOTH);
		req.setDoppler(doppler);

		Api.fsk_demodulation_settings.Builder fsk = Api.fsk_demodulation_settings.newBuilder();
		fsk.setDemodFskDeviation(5000);
		fsk.setDemodFskTransitionWidth(2000);
		fsk.setDemodFskUseDcBlock(false);

		req.setFskSettings(fsk);

		byte[] data = req.build().toByteArray();

		dos.writeByte(0x00);
		dos.writeByte(0x00); // rx request
		dos.writeInt(data.length);
		dos.write(data);

		InputStream is = s.getInputStream();
		DataInputStream dis = new DataInputStream(is);
		System.out.println("protocol: " + dis.readUnsignedByte());
		System.out.println("type: " + dis.readUnsignedByte());
		byte[] respData = new byte[dis.readInt()];
		System.out.println("length: " + respData.length);
		dis.readFully(respData);
		Api.Response response = Api.Response.parseFrom(respData);
		System.out.println("status: " + response.getStatus());
		System.out.println("details: " + response.getDetails());

		Thread t = new Thread(new Runnable() {

			@Override
			public void run() {
				Context ctx = new Context();
				ctx.setSoftBits(true);
				InputStreamSource is = new InputStreamSource(new BufferedInputStream(dis), ctx);
				HdlcReceiver r = new HdlcReceiver(new SoftToHard(is), 50000);
				while (true) {
					byte[] data;
					try {
						data = r.readBytes();
					} catch (IOException e) {
						e.printStackTrace();
						return;
					}
					int id = data[0] & 0xFF;
					Long start = null;
					synchronized (START_BY_ID) {
						start = START_BY_ID.remove(id);
					}
					if (start == null) {
						System.out.println("start is missing!! " + id);
						return;
					}
					System.out.println("received: " + data.length + " millis: " + (System.currentTimeMillis() - start));
				}
			}
		});
		t.start();

	}

	private static void runTx() throws UnknownHostException, IOException {
		Socket s = new Socket(HOSTNAME, 8091);
		OutputStream os = s.getOutputStream();
		DataOutputStream dos = new DataOutputStream(os);

		Api.doppler_settings.Builder doppler = Api.doppler_settings.newBuilder();
		doppler.setAltitude(0);
		doppler.setLatitude((int) (53.72 * 10E6));
		doppler.setLongitude((int) (47.57F * 10E6));
		doppler.addTle("LUCKY-7");
		doppler.addTle("1 44406U 19038W   20069.88080907  .00000505  00000-0  32890-4 0  9992");
		doppler.addTle("2 44406  97.5270  32.5584 0026284 107.4758 252.9348 15.12089395 37524");

		Api.TxRequest.Builder req = Api.TxRequest.newBuilder();
		req.setTxCenterFreq(633_944_000);
		req.setTxSamplingFreq(528000);
		req.setTxDumpFile(true);
		req.setTxOffset(10_000);
		req.setModType(Api.modem_type.GMSK);
		req.setModBaudRate(9600);
		req.setDoppler(doppler);

		Api.fsk_modulation_settings.Builder fsk = Api.fsk_modulation_settings.newBuilder();
		fsk.setModFskDeviation(5000);

		req.setFskSettings(fsk);

		byte[] data = req.build().toByteArray();

		dos.writeByte(0x00);
		dos.writeByte(0x05); // tx request
		dos.writeInt(data.length);
		dos.write(data);

		InputStream is = s.getInputStream();
		DataInputStream dis = new DataInputStream(is);
		System.out.println("protocol: " + dis.readUnsignedByte());
		System.out.println("type: " + dis.readUnsignedByte());
		byte[] respData = new byte[dis.readInt()];
		System.out.println("length: " + respData.length);
		dis.readFully(respData);
		Api.Response response = Api.Response.parseFrom(respData);
		System.out.println("status: " + response.getStatus());
		System.out.println("details: " + response.getDetails());

		for (int i = 0; i < 5; i++) {
			sendMessage(dos, dis);
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

	private static void sendMessage(DataOutputStream dos, DataInputStream dis) throws IOException {

		byte[] data = new byte[256];
		for (int i = 0; i < data.length; i++) {
			data[i] = (byte) i;
		}
		data[0] = (byte) id;

		byte[] sync = new byte[50];
		for (int i = 0; i < sync.length; i++) {
			sync[i] = (byte) 0b01010101;
		}

		HdlcTransmitter transmitter = new HdlcTransmitter();
		byte[] encoded = transmitter.encode(data);
		ArrayByteInput input = new ArrayByteInput(encoded);
		UnpackedToPacked u2p = new UnpackedToPacked(input, 1, Endianness.GR_MSB_FIRST);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		baos.write(sync);
		try {
			while (true) {
				baos.write(u2p.readByte());
			}
		} catch (Exception e) {
		}
		byte[] noSignal = new byte[10];
		baos.write(noSignal);

		byte[] encodedData = baos.toByteArray();

		Api.TxData.Builder req = Api.TxData.newBuilder();
		req.setData(ByteString.copyFrom(encodedData));

		byte[] toSend = req.build().toByteArray();

		long start = System.currentTimeMillis();
		synchronized (START_BY_ID) {
			START_BY_ID.put(id, start);
			id++;
		}

		dos.writeByte(0x00);
		dos.writeByte(0x04); // TX
		dos.writeInt(toSend.length);
		dos.write(toSend);

		int protocol = dis.readUnsignedByte();
		int type = dis.readUnsignedByte();
		byte[] respData = new byte[dis.readInt()];
		dis.readFully(respData);
		Api.Response response = Api.Response.parseFrom(respData);
	}

}
