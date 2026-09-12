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

	*setServerCPU { |server(Server.default), core = 1|
		if (core.notNil) {
			"taskset -cp % %".format(core, server.pid).unixCmd
		} {
			"taskset -cp %".format(server.pid).unixCmd
		}
	}
}