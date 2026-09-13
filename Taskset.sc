Taskset {
	classvar <>core = 1, <>enabled = true, <>server;

	*initClass {
		Class.initClassTree(Server);
		Class.initClassTree(ServerBoot);
		StartUp.add {
			if (this.enabled) {
				ServerBoot.add({ this.setServerCPU(this.server ? Server.default, this.core) }, \default);
			}
		}
	}

	*setServerCPU { |server(Server.default), core|
		if (server.serverRunning.not) {
			"Please boot server before trying to get or set the CPU core(s) it's supposed to run on.".warn
		};
		if (core.notNil) {
			"taskset -cp % %".format(core, server.pid).unixCmd
		} {
			"taskset -cp %".format(server.pid).unixCmd
		}
	}
}