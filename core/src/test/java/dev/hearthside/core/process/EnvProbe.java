package dev.hearthside.core.process;

/** Test-only child process: prints {@code NAME=value} (or {@code <unset>}) for each argument. */
public final class EnvProbe {
	public static void main(String[] args) throws InterruptedException {
		for (String name : args) {
			if (name.equals("--sleep")) {
				Thread.sleep(60_000);
				continue;
			}
			if (name.equals("--cwd")) {
				System.out.println("user.dir=" + System.getProperty("user.dir"));
				continue;
			}
			String value = System.getenv(name);
			System.out.println(name + "=" + (value == null ? "<unset>" : value));
		}
	}
}
