package rs.raf.pds.v5.z1;

import com.esotericsoftware.kryo.Kryo;

public class KryoUtil {
	public static void registerKryoClasses(Kryo kryo) {
		// Registruj sve tipove koje šalješ/čitaš
		kryo.register(String.class);
		kryo.register(Integer.class);
		kryo.register(Message.class);
		kryo.register(Auth.class);
	}
}