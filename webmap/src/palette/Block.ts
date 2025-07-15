export class Block {
    private readonly _block: number;
    private readonly _biome: number;
    private readonly _yPos: number;
    private readonly _minY: number;

    constructor(packed: bigint, minY: number) {
        this._block = Number((packed >> 32n) & 0xFFFFn);
        this._biome = Number((packed >> 16n) & 0xFFFFn);
        this._yPos = Number(packed & 0xFFFFn);
        this._minY = minY;
    }

    get block(): number {
        return this._block;
    }

    get biome(): number {
        return this._biome;
    }

    get yPos(): number {
        return this._yPos + this._minY;
    }
}
