package sd15;
import java.net.*;
import java.io.*;

public class ChatServer {
	private static boolean end = true; // trueのときクライアントの接続を受け付ける
	public static void main(String[] args) throws IOException {
		int serverPort = 50000;
		if (args.length == 1) serverPort = Integer.parseInt(args[0]);

		ServerSocket serverS = null;
		try {
			System.out.print(InetAddress.getLocalHost().getHostAddress());
			serverS = new ServerSocket(serverPort); // 指定されたポート番号のサーバを作成
			System.out.println(" " + serverS.getLocalPort());
		} catch (IOException e) {
			System.out.println("ポートにアクセスできません。");
			System.exit(1);
		}
		System.out.println("ChatMServer起動");
		while(end){
			new ChatMThread(serverS.accept()).start(); // クライアントからの接続を受け付ける
		}
		serverS.close();
	}
//	public static void setSwitchingAccept(boolean tf) {
//		end = tf;
//	}
//	public static boolean getSwitchingAccept() {
//		return end;
//	}
}