import {Block} from "./Block";

export class BlockInfo {
    public readonly BYTE_SIZE: number = 8;
    public readonly LONG_BYTES: number = 8;

    private readonly _data: Uint8Array;

    constructor(data: Uint8Array) {
        this._data = data;
    }

    get minY(): number {
        return this.getInt(8);
    }

    getBlock(index: number): Block {
        return new Block(this.getLong(12 + index * 8), this.minY);
    }

    private getInt(position: number): number {
        let val: number = 0;
        for (let i: number = 0; i < 4; i++) {
            val |= (this._data[position + i] & 0xFF) << (this.BYTE_SIZE * ((4 - 1) - i));
        }
        return val;
    }

    private getLong(position: number): bigint {
        let val = 0n;
        for (let i = 0; i < this.LONG_BYTES; i++) {
            val |= BigInt(this._data[position + i] & 0xFF) << BigInt(this.BYTE_SIZE * ((this.LONG_BYTES - 1) - i));
        }
        return val;
    }
}
