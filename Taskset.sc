Taskset {
	classvar <>core = 1, <>enabled = true;

	*initClass {
		Class.initClassTree(Server);
		Class.initClassTree(ServerBoot);
		StartUp.add {
			if (this.enabled) {
				ServerBoot.add({ this.setServerCPU(core: this.core) }, \default);
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